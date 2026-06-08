package com.example.server.strategy.impl;

import com.example.server.strategy.AiAnalysisStrategy;
import com.example.server.utils.AliyunAsrUtils;
import com.example.server.utils.DeepSeekUtils;
import com.example.server.utils.MinioUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component("defaultAiStrategy")
public class AliyunDeepSeekStrategy implements AiAnalysisStrategy {

    @Autowired
    private AliyunAsrUtils aliyunAsrUtils;

    @Autowired
    private DeepSeekUtils deepSeekUtils;

    @Autowired
    private MinioUtils minioUtils;

    @Value("${tool.ffmpeg.dir:}")
    private String ffmpegDir;

    @Override
    public String transcribe(String videoPath) {
        return processVideoToText(videoPath);
    }

    @Override
    public String generateSummary(String videoPath) {
        String text = processVideoToText(videoPath);
        if (text.startsWith("ERROR:")) {
            return text;
        }
        return deepSeekUtils.analyzeContent("视频转写文本如下，请生成时间轴 JSON：\n" + text);
    }

    @Override
    public String generateSummaryFromText(String transcriptText) {
        if (transcriptText == null || transcriptText.isBlank()) {
            return "ERROR: transcript text is empty";
        }
        if (transcriptText.startsWith("ERROR:") || transcriptText.startsWith("识别失败")) {
            return transcriptText;
        }
        return deepSeekUtils.analyzeContent("视频转写文本如下，请生成时间轴 JSON：\n" + transcriptText);
    }

    private String processVideoToText(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) {
            return "ERROR: video path is empty";
        }

        // MinIO Bucket 为 Private 模式，HTTP 直连会被 403 拒绝
        // 通过 MinIO SDK（携带 accessKey/secretKey）下载到本地临时文件，再给 FFmpeg 处理
        String localVideoPath = null;
        boolean needCleanupLocalVideo = false;

        if (inputPath.startsWith("http")) {
            try {
                String objectName = minioUtils.extractObjectName(inputPath);
                localVideoPath = System.getProperty("java.io.tmpdir") + File.separator
                        + "temp_video_" + UUID.randomUUID() + ".mp4";
                System.out.println("[AI] downloading video from MinIO: " + objectName);
                minioUtils.downloadObject(objectName, localVideoPath);
                needCleanupLocalVideo = true;
                System.out.println("[AI] video downloaded to: " + localVideoPath);
            } catch (Exception e) {
                return "ERROR: failed to download video from MinIO: " + e.getMessage();
            }
        } else {
            localVideoPath = inputPath;
            File localFile = new File(localVideoPath);
            if (!localFile.exists()) {
                return "ERROR: video file not found: " + localVideoPath;
            }
        }

        String outputMp3Path = System.getProperty("java.io.tmpdir") + File.separator + "temp_" + UUID.randomUUID() + ".mp3";
        String publicAudioUrl = null;

        try {
            System.out.println("[AI] extracting audio: " + localVideoPath);

            boolean success = extractAudio(localVideoPath, outputMp3Path);
            if (!success) {
                return "ERROR: FFmpeg failed to extract audio";
            }

            // 上传到 MinIO 并获取预签名 URL（供百炼 ASR 下载）
            File mp3File = new File(outputMp3Path);
            publicAudioUrl = minioUtils.uploadTempAudio(mp3File);
            System.out.println("[AI] audio uploaded to MinIO: " + publicAudioUrl);

            return aliyunAsrUtils.audioToText(publicAudioUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: video transcription failed: " + e.getMessage();
        } finally {
            // 清理本地临时 mp3
            File mp3 = new File(outputMp3Path);
            if (mp3.exists()) {
                mp3.delete();
            }
            // 清理本地临时视频（从 MinIO 下载的）
            if (needCleanupLocalVideo && localVideoPath != null) {
                File localVideo = new File(localVideoPath);
                if (localVideo.exists()) {
                    localVideo.delete();
                    System.out.println("[AI] temp local video deleted: " + localVideoPath);
                }
            }
            // 清理 MinIO 中的临时音频
            if (publicAudioUrl != null) {
                minioUtils.removeByUrl(publicAudioUrl);
            }
        }
    }

    private boolean extractAudio(String inputPath, String outputPath) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add(resolveFfmpegCommand());
            command.add("-y");
            command.add("-i");
            command.add(inputPath);
            command.add("-vn");
            command.add("-acodec");
            command.add("libmp3lame");
            command.add("-q:a");
            command.add("2");
            command.add(outputPath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String resolveFfmpegCommand() {
        if (ffmpegDir == null || ffmpegDir.isBlank()) {
            return "ffmpeg";
        }
        File ffmpegExe = new File(ffmpegDir, "ffmpeg.exe");
        if (ffmpegExe.exists()) {
            return ffmpegExe.getAbsolutePath();
        }
        File ffmpegBin = new File(ffmpegDir, "ffmpeg");
        if (ffmpegBin.exists()) {
            return ffmpegBin.getAbsolutePath();
        }
        return "ffmpeg";
    }
}
