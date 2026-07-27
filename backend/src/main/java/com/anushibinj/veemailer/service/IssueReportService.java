package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.IssueReportResponseDto;
import com.anushibinj.veemailer.dto.IssueUploadConfigDto;
import com.anushibinj.veemailer.model.IssueReport;
import com.anushibinj.veemailer.model.IssueStatus;
import com.anushibinj.veemailer.repository.IssueReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueReportService {

    private final IssueReportRepository issueReportRepository;

    @Value("${veemailer.issues.max-screenshot-bytes:1048576}")
    private long maxScreenshotBytes;

    @Value("${veemailer.issues.max-upload-bytes:5242880}")
    private long maxUploadBytes;

    public IssueReportResponseDto submitIssue(
            String message,
            String reporterEmail,
            MultipartFile screenshot,
            String authenticatedEmail) {
        String normalizedMessage = normalizeToNull(message);
        String normalizedEmail = normalizeToNull(reporterEmail);
        String effectiveEmail = normalizedEmail != null ? normalizedEmail : normalizeToNull(authenticatedEmail);

        boolean hasScreenshot = screenshot != null && !screenshot.isEmpty();
        if (normalizedMessage == null && !hasScreenshot) {
            throw new IllegalArgumentException("Either message or screenshot is required.");
        }

        byte[] screenshotBytes = null;
        String screenshotContentType = null;
        String screenshotFileName = null;
        if (hasScreenshot) {
            if (screenshot.getSize() > maxUploadBytes) {
                throw new IllegalArgumentException(
                        "Screenshot is " + formatBytes(screenshot.getSize())
                                + ", which exceeds the upload limit of " + formatBytes(maxUploadBytes) + "."
                );
            }
            String normalizedContentType = normalizeToNull(screenshot.getContentType());
            if (normalizedContentType == null || !normalizedContentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalArgumentException("Only image screenshots are supported.");
            }
            byte[] originalBytes = readMultipartBytes(screenshot);
            CompressionResult compressed = compressImageToLimit(originalBytes, normalizedContentType);
            screenshotBytes = compressed.bytes();
            screenshotContentType = compressed.contentType();
            screenshotFileName = normalizeToNull(screenshot.getOriginalFilename());
        }

        IssueReport toSave = IssueReport.builder()
                .reporterEmail(effectiveEmail)
                .message(normalizedMessage)
                .screenshotData(screenshotBytes)
                .screenshotContentType(screenshotContentType)
                .screenshotFileName(screenshotFileName)
                .status(IssueStatus.OPEN)
                .build();
        IssueReport saved = issueReportRepository.save(toSave);
        return toResponseDto(saved, false);
    }

    public IssueUploadConfigDto getUploadConfig() {
        return IssueUploadConfigDto.builder()
                .maxUploadBytes(maxUploadBytes)
                .maxStoredScreenshotBytes(maxScreenshotBytes)
                .build();
    }

    public List<IssueReportResponseDto> listIssues() {
        return issueReportRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(issue -> toResponseDto(issue, true))
                .toList();
    }

    public IssueReportResponseDto updateStatus(UUID issueId, IssueStatus status) {
        IssueReport issue = issueReportRepository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        issue.setStatus(status);
        IssueReport saved = issueReportRepository.save(issue);
        return toResponseDto(saved, true);
    }

    private IssueReportResponseDto toResponseDto(IssueReport issue, boolean includeScreenshotData) {
        byte[] screenshotData = issue.getScreenshotData();
        boolean hasScreenshot = screenshotData != null && screenshotData.length > 0;
        String screenshotBase64 = null;
        if (includeScreenshotData && hasScreenshot) {
            screenshotBase64 = Base64.getEncoder().encodeToString(screenshotData);
        }

        return IssueReportResponseDto.builder()
                .id(issue.getId())
                .reporterEmail(issue.getReporterEmail())
                .message(issue.getMessage())
                .status(issue.getStatus())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .hasScreenshot(hasScreenshot)
                .screenshotContentType(issue.getScreenshotContentType())
                .screenshotBase64(screenshotBase64)
                .build();
    }

    private byte[] readMultipartBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read uploaded screenshot.", ex);
        }
    }

    private CompressionResult compressImageToLimit(byte[] bytes, String contentType) {
        if (bytes.length <= maxScreenshotBytes) {
            return new CompressionResult(bytes, contentType);
        }

        BufferedImage source;
        try {
            source = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse screenshot image.", ex);
        }
        if (source == null) {
            throw new IllegalArgumentException("Uploaded screenshot is not a valid image.");
        }

        String formatName = resolveImageFormat(contentType);
        BufferedImage current = source;
        float jpegQuality = 0.9f;

        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] encoded = encodeImage(current, formatName, jpegQuality);
            if (encoded.length <= maxScreenshotBytes) {
                return new CompressionResult(encoded, contentTypeForFormat(formatName, contentType));
            }

            int nextWidth = Math.max(320, (int) (current.getWidth() * 0.82));
            int nextHeight = Math.max(240, (int) (current.getHeight() * 0.82));
            if (nextWidth == current.getWidth() && nextHeight == current.getHeight()) {
                break;
            }

            current = scaleImage(current, nextWidth, nextHeight);
            if ("jpg".equals(formatName)) {
                jpegQuality = Math.max(0.45f, jpegQuality - 0.08f);
            }
        }

        throw new IllegalArgumentException(
                "Screenshot could not be compressed below the storage limit of "
                        + formatBytes(maxScreenshotBytes)
                        + ". Please upload a smaller image."
        );
    }

    private byte[] encodeImage(BufferedImage image, String formatName, float jpegQuality) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!"jpg".equals(formatName)) {
                ImageIO.write(image, formatName, out);
                return out.toByteArray();
            }

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
            if (!writers.hasNext()) {
                throw new IllegalArgumentException("Image writer is not available for JPEG format.");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(jpegQuality);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to compress screenshot image.", ex);
        }
    }

    private BufferedImage scaleImage(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height,
                source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType());
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private String resolveImageFormat(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return "jpg";
        }
        if (normalized.contains("png")) {
            return "png";
        }
        return "jpg";
    }

    private String contentTypeForFormat(String formatName, String fallbackContentType) {
        if ("png".equals(formatName)) {
            return "image/png";
        }
        if ("jpg".equals(formatName)) {
            return "image/jpeg";
        }
        return fallbackContentType;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.2f MB", mb);
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CompressionResult(byte[] bytes, String contentType) {}
}
