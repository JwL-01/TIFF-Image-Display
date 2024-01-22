import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import static java.lang.System.exit;
import static javax.swing.JOptionPane.showOptionDialog;

public class Main {

    static byte[] data = null;

    static public Color[] color = null;

    static public int imageHeight = 0;
    static public int imageWidth = 0;

    static public java.util.List<Integer> stripOffsets = new ArrayList<Integer>();
    static int index;
    static public BufferedImage tiffImage;

    static boolean byteOrder;

    static class Type {
        public String name;
        public int size;

        public Type(String n, int s) {
            name = n;
            size = s;
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("TIFF Image Display");
        FileDialog dialog = new FileDialog(frame, "Open File", FileDialog.LOAD);

        Button openFileButton = new Button("Open .tif File");
        Button exitButton = new Button("Exit");

        openFileButton.addActionListener(e -> {
            dialog.setVisible(true);

            String filePath = dialog.getDirectory() + dialog.getFile();

            data = null;
            color = null;

            decode(filePath);
            int printOptionsResult = 0;

            tiffImage = new BufferedImage(imageHeight, imageWidth, BufferedImage.TYPE_INT_RGB);
            int num = 0;
            for (int i = 0; i < imageWidth; i++){
                for (int k = 0; k < imageHeight; k++){
                    tiffImage.setRGB(k, i, color[num].getRGB());
                    num++;
                }
            }
            BufferedImage originalImage = new BufferedImage(tiffImage.getWidth(), tiffImage.getHeight(), tiffImage.getType());
            BufferedImage greyTiffImage = new BufferedImage(tiffImage.getWidth(), tiffImage.getHeight(), tiffImage.getType());
            BufferedImage halfBrightImage = new BufferedImage(tiffImage.getWidth(), tiffImage.getHeight(), tiffImage.getType());
            BufferedImage orderedDitheringImage = new BufferedImage(tiffImage.getWidth(), tiffImage.getHeight(), tiffImage.getType());
            BufferedImage autoLevelImage = new BufferedImage(tiffImage.getWidth(), tiffImage.getHeight(), tiffImage.getType());

            originalImage = tiffImage;
            SetToGreyScaleImage(greyTiffImage);
            SetToHalfBrightImage(halfBrightImage);
            SetToDitheredImage(orderedDitheringImage);
            SetToAutoLevelImage(autoLevelImage);

            printOptionsResult = printImageOptions(originalImage, greyTiffImage);
            // To open a new image press option 0 and clear the StripOffsets for the new image
            if(printOptionsResult == 0){
                stripOffsets.clear();
                return;
            }

            if(printOptionsResult == 1){
                stripOffsets.clear();
                printImageOptions(halfBrightImage, greyTiffImage);
            }

            if(printOptionsResult == 1){
                stripOffsets.clear();
                printImageOptions(greyTiffImage, orderedDitheringImage);
            }

            if(printOptionsResult == 1){
                stripOffsets.clear();
                printImageOptions(originalImage, autoLevelImage);
            }

            // To close the program press option 2
            if(printOptionsResult == 2){
                System.exit(0);
            }
        });

        exitButton.addActionListener(e -> {
            exit(0);
        });

        frame.add(openFileButton, BorderLayout.CENTER);
        frame.add(exitButton, BorderLayout.SOUTH);
        frame.pack();
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                exit(0);
            }
        });
        frame.setSize(300, 300);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    static private Type[] dataTypeArray = {
            new Type("???", 0),
            new Type("byte", 1),
            new Type("ascii", 1),
            new Type("short", 2),
            new Type("long", 4),
            new Type("rational", 8),
            new Type("sbyte", 1),
            new Type("undefined", 1),
            new Type("sshort", 1),
            new Type("slong", 1),
            new Type("srational", 1),
            new Type("float", 4),
            new Type("double", 8)
    };

    static public void decode(String path) {
        //readAllBytes
        try {
            data = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //decode header. To find the position of first IFD and if it is II or MM
        int imageFileDecode = imageFileHeader();

        //decode IFD and return address of next IFD
        while (imageFileDecode != 0) {
            imageFileDecode = imageFileDecoder(imageFileDecode);
        }
    }

    public static void SetToHalfBrightImage(BufferedImage image){
        for (int y = 0; y < image.getHeight(); y++){
            for (int x = 0; x < image.getWidth(); x++){
                int R = color[y * imageHeight + x].getRed();
                int G = color[y * imageHeight + x].getGreen();
                int B = color[y * imageHeight + x].getBlue();

                R /= 2;
                G /= 2;
                B /= 2;
                int newHalfRGB = (R << 16) | (G << 8) | B;

                image.setRGB(x, y, newHalfRGB);
            }
        }
    }

    public static void SetToAutoLevelImage(BufferedImage image){

        int[] min = {255, 255, 255};
        int[] max = {0, 0, 0};

        // Find the minimum and maximum values for each RGB channel
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int R = color[y * imageHeight + x].getRed();
                int G = color[y * imageHeight + x].getGreen();
                int B = color[y * imageHeight + x].getBlue();

                min[0] = Math.min(min[0], R);
                min[1] = Math.min(min[1], G);
                min[2] = Math.min(min[2], B);

                max[0] = Math.max(max[0], R);
                max[1] = Math.max(max[1], G);
                max[2] = Math.max(max[2], B);
            }
        }

        // Print the minimum and maximum values for debugging
//        System.out.println("Red Min: " + min[0] + ", Red Max: " + max[0]);
//        System.out.println("Green Min: " + min[1] + ", Green Max: " + max[1]);
//        System.out.println("Blue Min: " + min[2] + ", Blue Max: " + max[2]);

        // Normalize and apply the auto-level adjustment
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int R = color[y * imageHeight + x].getRed();
                int G = color[y * imageHeight + x].getGreen();
                int B = color[y * imageHeight + x].getBlue();

                R = normalize(R, min[0], max[0]);
                G = normalize(G, min[1], max[1]);
                B = normalize(B, min[2], max[2]);

                int newRGB = (R << 16) | (G << 8) | B;
                image.setRGB(x, y, newRGB);
            }
        }
    }

    private static int normalize(int value, int minValue, int maxValue){
        double normalizedValue = (double)(value - minValue) / (double)(maxValue - minValue);
        return (int)(normalizedValue * 255);
    }

    public static void SetToGreyScaleImage(BufferedImage image){
        for (int y = 0; y < image.getHeight(); y++){
            for (int x = 0; x < image.getWidth(); x++){
                int R = color[y * imageHeight + x].getRed();
                int G = color[y * imageHeight + x].getGreen();
                int B = color[y * imageHeight + x].getBlue();

                int Y = (int)(0.299 * R + 0.587 * G + 0.114 * B);
                int greyScale  = (Y << 16) | (Y << 8) | Y;
                image.setRGB(x, y, greyScale);
            }
        }
    }

    static public void SetToDitheredImage(BufferedImage image){
        int x,y;
        //dither matrix 1,3,0,2
        int[][] ditherMatrix = {{1, 3}, {0, 2}};
        int[][] originalSize = new int[imageWidth][imageHeight];

        for(y = 0; y < image.getHeight(); y++) {
            for (x = 0; x < image.getWidth(); x++) {
                int R = color[y * imageHeight + x].getRed();
                int G = color[y * imageHeight + x].getGreen();
                int B = color[y * imageHeight + x].getBlue();

                int temp = (int) (0.299 * R + 0.587 * G + 0.114 * B);
                temp = (int) Math.floor(temp / (256.0 / 5));
                originalSize[y][x] = temp;

                if (originalSize[y][x] > ditherMatrix[x % 2][y % 2]){
                    originalSize[y][x] = 255;
                }
                else
                {
                    originalSize[y][x] = 0;
                }

                temp = originalSize[y][x];
                temp = (temp << 16) | (temp << 8) | temp;
                image.setRGB(x, y, temp);
            }
        }
    }

    static public BufferedImage combineImages(BufferedImage image1, BufferedImage image2)
    {
        ImageIcon imageIcon1 = new ImageIcon(image1);
        ImageIcon imageIcon2 = new ImageIcon(image2);

        int imageWidth = imageIcon1.getIconWidth() + imageIcon2.getIconWidth();
        int imageHeight = Math.max(imageIcon1.getIconHeight(), imageIcon2.getIconHeight());
        BufferedImage combinedImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combinedImage.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.drawImage(imageIcon1.getImage(), 0, 0, null);
        g.drawImage(imageIcon2.getImage(), imageIcon1.getIconWidth(), 0, null);
        g.dispose();
        return combinedImage;
    }

    static public int printImageOptions(BufferedImage image1, BufferedImage image2)
    {
        BufferedImage combinedImage = combineImages(image1, image2);

        String[] options = {"Open New Image", "Next" ,"Exit"};
        return showOptionDialog(null,null, "Display TIFF Image", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, new ImageIcon(combinedImage), options, options[0]);
    }

    static private int imageFileHeader() {
        //firstly check the image is TIFF or not
        String orderOfByte = getString(0, 2);
        if (orderOfByte.equals("II"))
            byteOrder = true;
        else if (orderOfByte.equals("MM"))
            byteOrder = false;
        else
            exit(3);//if it is not MM / II

        int version = getInt(2, 2);

        if (version != 42)
            exit(2);//if the image is not TIFF

        return getInt(4, 4);
    }

    static public int imageFileDecoder(int position) {
        int temp = position;
        int dataEntryCount = getInt(temp, 2);
        temp += 2;
        for (int i = 0; i < dataEntryCount; i++) {
            dataEntry(temp);
            temp += 12;
        }
        strips();
        int pNext = getInt(temp, 4);
        return pNext;
    }

    static public void dataEntry(int position) {
        //declare tag index
        int indexOfTag = getInt(position, 2);
        //declare type index
        int indexOfType = getInt(position + 2, 2);
        //declare number of count
        int count = getInt(position + 4, 4);
        //get position of next data
        int nextData = position + 8;
        int sizeInTotal = dataTypeArray[indexOfType].size * count;
        if (sizeInTotal > 4)
            nextData = getInt(nextData, 4);
        getDataEntryValue(indexOfTag, indexOfType, count, nextData);
    }

    static private void getDataEntryValue(int tagIndex, int typeIndex, int count, int pdata) {
        int typeSize = dataTypeArray[typeIndex].size;
        switch (tagIndex) {
            case 256://ImageWidth
                imageHeight = getInt(pdata, typeSize);
                break;
            case 257://ImageLength
                if (typeIndex == 3)//short
                    imageWidth = getInt(pdata, typeSize);
                break;
            case 273://StripOffsets
                for (int i = 0; i < count; i++) {
                    int v = getInt(pdata + i * typeSize, typeSize);
                    stripOffsets.add(v);
                }
                break;
            default:
                break;
        }
    }

    static private void strips() {
        color = new Color[imageWidth * imageHeight];
        index = stripOffsets.get(0);
        int R = 0;
        int G = 0;
        int B = 0;
        for (int i = 0; i <= (imageHeight * imageWidth) - 1; i++) {
            int x = Byte.toUnsignedInt(data[index]);
            R = x;
            x = Byte.toUnsignedInt(data[index + 1]);
            G = x;
            x = Byte.toUnsignedInt(data[index + 2]);
            B = x;
            index += 3;
            color[i] = new Color(R, G, B);
        }
    }

    static private int getInt(int startingPosition, int length) {
        int returnedResult = 0;
        // for "II"
        if (byteOrder)
            for (int i = 0; i < length; i++) {
                int x = Byte.toUnsignedInt(data[startingPosition + i]);
                returnedResult |= x << i * 8;
            }
            // for "MM"
        else
            for (int i = 0; i < length; i++){
                int x = Byte.toUnsignedInt(data[startingPosition +length-1- i]);
                returnedResult |= x << i * 8;
            }
        return returnedResult;
    }

    static private String getString(int startingPosition, int length) {
        String temp = "";
        for (int i = 0; i < length; i++)
            temp += (char) data[startingPosition];
        return temp;
    }
}