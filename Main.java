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

    static public int imageWidth = 0;
    static public int imageLength = 0;

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
            int printResult = 0;

            tiffImage = new BufferedImage(imageWidth, imageLength, BufferedImage.TYPE_INT_RGB);
            int num = 0;
            for (int i = 0; i < imageLength; i++){
                for (int k = 0; k < imageWidth; k++){
                    tiffImage.setRGB(k, i, color[num].getRGB());
                    num++;
                }
            }

            printResult = printImageOptions();

            // To open a new image press option 0 and clear the StripOffsets for the new image
            if(printResult == 0){
                stripOffsets.clear();
                return;
            }
            // To close the program press option 1
            if(printResult == 1){
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

    static public int printImageOptions()
    {
        ImageIcon imageIcon = new ImageIcon(tiffImage);
        String[] options = {"Open New Image", "Exit"};
        int dialogResult = showOptionDialog(null,null, "Display TIFF Image", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, imageIcon, options, options[0]);
        return dialogResult;
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
                imageWidth = getInt(pdata, typeSize);
                break;
            case 257://ImageLength
                if (typeIndex == 3)//short
                    imageLength = getInt(pdata, typeSize);
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
        color = new Color[imageLength * imageWidth];
        index = stripOffsets.get(0);
        int R = 0;
        int G = 0;
        int B = 0;
        for (int i = 0; i <= (imageWidth * imageLength) - 1; i++) {
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