package com.example.server.strategy.impl;

import com.example.server.strategy.AiAnalysisStrategy;
import com.example.server.utils.AliyunAsrUtils;
import com.example.server.utils.DeepSeekUtils;
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

    private String processVideoToText(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) {
            return "ERROR: video path is empty";
        }

        if (!inputPath.startsWith("http")) {
            File localFile = new File(inputPath);
            if (!localFile.exists()) {
                return "ERROR: video file not found: " + inputPath;
            }
        }

        String outputMp3Path = System.getProperty("java.io.tmpdir") + File.separator + "temp_" + UUID.randomUUID() + ".mp3";

        try {
            System.out.println("[AI] extracting audio: " + inputPath);

            boolean success = extractAudio(inputPath, outputMp3Path);
            if (!success) {
                return "ERROR: FFmpeg failed to extract audio";
            }

            return aliyunAsrUtils.audioToText(outputMp3Path);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: video transcription failed: " + e.getMessage();
        } finally {
            File mp3 = new File(outputMp3Path);
            if (mp3.exists()) {
                mp3.delete();
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
