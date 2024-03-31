package com.uetty.common.tool.core.image;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;

public class GifWriter {

    protected BufferedImage[] bufferedImages;
    protected int outputWidth;
    protected int outputHeight;
    protected int[] durations;

    protected int imageType = BufferedImage.TYPE_INT_ARGB;

    private static final int DEFAULT_DURATION = 30;

    private GifWriter(BufferedImage[] bufferedImages, int outputWidth, int outputHeight, int[] durations) {
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        // 最小为10，默认为30
        if (durations == null) {
            durations = new int[bufferedImages.length];
            Arrays.fill(durations, DEFAULT_DURATION);
        }
        if (durations.length < bufferedImages.length) {
            int[] newDurations = new int[bufferedImages.length];
            System.arraycopy(durations, 0, newDurations, 0, durations.length);
            Arrays.fill(newDurations, durations.length, newDurations.length, DEFAULT_DURATION);
            durations = newDurations;
        }
        if (durations.length > bufferedImages.length) {
            int[] newDurations = new int[bufferedImages.length];
            System.arraycopy(durations, 0, newDurations, 0, bufferedImages.length);
            durations = newDurations;
        }
        this.durations = durations;
        this.bufferedImages = bufferedImages;
    }

    public static GifWriter create(BufferedImage[] bufferedImages, int outputWidth, int outputHeight, int[] durations) {
        return new GifWriter(bufferedImages, outputWidth, outputHeight, durations);
    }

    public static GifWriter create(InputStream[] inputStreams, int outputWidth, int outputHeight, int[] durations) {
        BufferedImage[] bufferedImages = new BufferedImage[inputStreams.length];
        for (int i = 0; i < inputStreams.length; i++) {
            try {
                bufferedImages[i] = ImageIO.read(inputStreams[i]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new GifWriter(bufferedImages, outputWidth, outputHeight, durations);
    }

    public static GifWriter create(File[] files, int outputWidth, int outputHeight, int[] durations) {
        BufferedImage[] bufferedImages = new BufferedImage[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                bufferedImages[i] = ImageIO.read(files[i]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new GifWriter(bufferedImages, outputWidth, outputHeight, durations);
    }

    public static GifWriter create(String[] paths, int outputWidth, int outputHeight, int[] durations) {
        BufferedImage[] bufferedImages = new BufferedImage[paths.length];
        for (int i = 0; i < paths.length; i++) {
            try {
                bufferedImages[i] = ImageIO.read(new File(paths[i]));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new GifWriter(bufferedImages, outputWidth, outputHeight, durations);
    }

    public GifWriter imageType(int imageType) {
        this.imageType = imageType;
        return this;
    }

    public void write(ImageOutputStream outputStream) throws IOException {
        ImageWriter imageWriter = ImageIO.getImageWritersBySuffix("gif").next();

        ImageWriteParam imageWriteParam = imageWriter.getDefaultWriteParam();
        ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
        IIOMetadata imageMetaData = imageWriter.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam);
        String metaFormatName = imageMetaData.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);
        IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");
        IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
        commentsNode.setAttribute("CommentExtension", "Created by GifWriter");
        imageMetaData.setFromTree(metaFormatName, root);

        imageWriter.setOutput(outputStream);
        imageWriter.prepareWriteSequence(null);
        for (int i = 0; i < bufferedImages.length; i++) {
            BufferedImage bufferedImage = bufferedImages[i];
            if (bufferedImage.getWidth() != outputWidth || bufferedImage.getHeight() != outputHeight) {
                bufferedImage = zoom(bufferedImage, outputWidth, outputHeight);
            }

            ImageWriteParam param = imageWriter.getDefaultWriteParam();
            IIOMetadata metadata = imageWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromBufferedImageType(imageType), param);
            metaFormatName = metadata.getNativeMetadataFormatName();
            root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);
            IIOMetadataNode graphicControlExtensionNode = getNode(root, "GraphicControlExtension");
            int durationIndex = (i + durations.length - 1) %  durations.length;
            // 以10毫秒为单位
            int count10ms = durations[durationIndex] / 10;
            count10ms = count10ms < 1 ? 1 : Math.min(count10ms, 10000);
            graphicControlExtensionNode.setAttribute("delayTime", Integer.toString(count10ms));
            metadata.setFromTree(metaFormatName, root);

            imageWriter.writeToSequence(new IIOImage(bufferedImage, null, metadata), param);
        }

        imageWriter.endWriteSequence();
    }

    private BufferedImage zoom(BufferedImage bufferedImage, int outputWidth, int outputHeight) {
        BufferedImage result = new BufferedImage(outputWidth, outputHeight, bufferedImage.getType());
        Graphics2D graphics2D = result.createGraphics();
        graphics2D.drawImage(bufferedImage, 0, 0, outputWidth, outputHeight, null);
        graphics2D.dispose();
        return result;
    }

    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().compareToIgnoreCase(nodeName) == 0) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return (node);
    }

    public static void main(String[] args) {
        String[] imageFileNames = {"/Users/vince/temp/aaa/1.jpeg", "/Users/vince/temp/aaa/2.jpeg", "/Users/vince/temp/aaa/3.jpeg"};
        // 每个图像帧的持续时间
        int[] durations = {2000, 1000, 5000};
        // 输出图片宽度
        int outputWidth = 800;
        // 输出图片高度
        int outputHeight = 500;

        GifWriter gifWriter = GifWriter.create(imageFileNames, outputWidth, outputHeight, durations);

        try (ImageOutputStream os = new FileImageOutputStream(new File("/Users/vince/temp/aaa/out.gif"))) {
            gifWriter.write(os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
