package com.example.server.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class YtDlpUtils {

    @Value("${tool.ytdlp.path}")
    private String ytDlpPath;

    @Value("${tool.ffmpeg.dir}")
    private String ffmpegDir;

    @Value("${tool.you-get.path:you-get}")
    private String youGetPath;

    public File downloadVideo(String url) throws Exception {
        try {
            return downloadWithYtDlp(url);
        } catch (Exception ytDlpError) {
            System.err.println("⚠️ yt-dlp 下载失败，尝试 you-get 兜底: " + ytDlpError.getMessage());
            return downloadWithYouGet(url);
        }
    }

    private File downloadWithYtDlp(String url) throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir");
        String outputName = UUID.randomUUID().toString() + ".mp4";
        String outputPath = tempDir + File.separator + outputName;

        System.out.println("⬇️ [yt-dlp] 开始下载 (智能模式): " + url);

        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);

        //伪装头 (保留，防止直接被 ban)
        command.add("--user-agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        command.add("--referer");
        command.add("https://www.bilibili.com/");

        //强制转码 mp4 (这是唯一的硬性要求)
        command.add("--recode-video");
        command.add("mp4");

        command.add("--ffmpeg-location");
        command.add(ffmpegDir);

        command.add("-o");
        command.add(outputPath);

        //忽略证书和播放列表
        command.add("--no-check-certificate");
        command.add("--no-playlist");

        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder logs = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR") || line.contains("Downloading") || line.contains("[Merger]")) {
                    System.out.println("cmd > " + line);
                }
                logs.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("yt-dlp 下载失败: " + logs.toString());
        }

        File downloadedFile = new File(outputPath);
        if (!downloadedFile.exists()) {
            throw new RuntimeException("下载显示成功但文件未生成");
        }

        System.out.println("✅ [yt-dlp] 下载完成: " + (downloadedFile.length() / 1024) + "KB");
        return downloadedFile;
    }

    private File downloadWithYouGet(String url) throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir");
        String outputName = UUID.randomUUID().toString();

        System.out.println("⬇️ [you-get] 开始下载: " + url);

        List<String> command = new ArrayList<>();
        command.add(youGetPath);
        command.add("--no-caption");
        command.add("-o");
        command.add(tempDir);
        command.add("-O");
        command.add(outputName);
        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder logs = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR") || line.contains("Downloading")) {
                    System.out.println("cmd > " + line);
                }
                logs.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("you-get 下载失败: " + logs.toString());
        }

        // you-get 会自动加扩展名，需要找到生成的文件
        File downloadedFile = new File(tempDir, outputName + ".mp4");
        if (!downloadedFile.exists()) {
            // 可能被加了其他扩展名
            File[] candidates = new File(tempDir).listFiles(f ->
                    f.getName().startsWith(outputName));
            if (candidates != null && candidates.length > 0) {
                downloadedFile = candidates[0];
            }
        }

        if (!downloadedFile.exists()) {
            throw new RuntimeException("you-get 下载完成但文件未找到");
        }

        System.out.println("✅ [you-get] 下载完成: " + (downloadedFile.length() / 1024) + "KB");
        return downloadedFile;
    }
}