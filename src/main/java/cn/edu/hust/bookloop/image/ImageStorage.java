package cn.edu.hust.bookloop.image;

import cn.edu.hust.bookloop.publish.PublishFailure;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Path root;
    public ImageStorage(@Value("${bookloop.upload-dir}") String directory) { root = Path.of(directory).toAbsolutePath().normalize(); }
    public record Prepared(byte[] bytes, String extension, String mime) {}

    public Prepared prepare(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw new PublishFailure("每张照片须大于0且不超过5MB。");
        try (var source = file.getInputStream()) {
            byte[] input = source.readNBytes(MAX_BYTES + 1);
            if (input.length > MAX_BYTES) throw new PublishFailure("每张照片不能超过5MB。");
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new PublishFailure("无法识别照片，请上传真实的JPG、PNG或WebP图片。");
                var reader = readers.next();
                BufferedImage decoded = null;
                try {
                    reader.setInput(stream, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String actualMime = switch (format) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "webp" -> "image/webp";
                        default -> throw new PublishFailure("只支持JPG、PNG或WebP照片。");
                    };
                    if (!actualMime.equals(file.getContentType())) throw new PublishFailure("照片内容与文件类型不一致，请重新导出图片后上传。");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 10000 || height > 10000 || (long) width * height > 20_000_000) {
                        throw new PublishFailure("照片尺寸过大，请压缩至2000万像素以内，且单边不超过10000像素。");
                    }
                    final boolean[] warning = {false};
                    reader.addIIOReadWarningListener((r, message) -> warning[0] = true);
                    decoded = reader.read(0);
                    if (decoded == null || warning[0]) throw new PublishFailure("照片不完整或已损坏，请重新导出后上传。");
                    // Re-encode decoded pixels: no original names, metadata or trailing executable payloads are retained.
                    String extension = actualMime.equals("image/jpeg") ? "jpg" : "png";
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(decoded, extension, output)) throw new IOException("No image encoder");
                    if (output.size() > MAX_BYTES) throw new PublishFailure("处理后的照片超过5MB，请缩小尺寸后重新上传。");
                    return new Prepared(output.toByteArray(), extension, extension.equals("jpg") ? "image/jpeg" : "image/png");
                } finally {
                    if (decoded != null) decoded.flush();
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new PublishFailure("照片无法解码，请检查文件是否完整。", exception);
        }
    }

    public String save(Prepared image) {
        Path temp = null;
        try {
            Files.createDirectories(root);
            Path directory = root.resolve("listings");
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new IOException("Symbolic image directory is not supported");
            temp = Files.createTempFile(directory, ".pending-", ".tmp");
            Files.write(temp, image.bytes());
            String key = "listings/" + UUID.randomUUID() + "." + image.extension();
            Files.move(temp, root.resolve(key), StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException exception) {
            throw new PublishFailure("图片保存失败，请稍后重试。", exception);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); }
                catch (IOException exception) { log.warn("Could not remove temporary image {}", temp, exception); }
            }
        }
    }

    public byte[] read(String key) throws IOException {
        Path file = resolve(key);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().startsWith(root.toRealPath()) || Files.size(file) > MAX_BYTES) {
            throw new FileNotFoundException("Image unavailable");
        }
        return Files.readAllBytes(file);
    }

    public void discard(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException exception) { log.error("Could not remove rolled-back image {}; cleanup required", key, exception); }
    }

    private Path resolve(String key) throws IOException {
        if (key == null || !key.matches("listings/[0-9a-f-]{36}\\.(jpg|png)")) throw new FileNotFoundException("Invalid storage key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(path.getParent())) throw new FileNotFoundException("Invalid image path");
        return path;
    }
}
