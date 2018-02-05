package util;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by monadiab on 4/12/16.
 */
public class IO {

    public static ArrayList<String> readCoNLLFile(String coNLLFile) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(coNLLFile),"UTF-8"));
        ArrayList<String> sentences = new ArrayList<String>();
        String line2read = "";
        int counter = 0;
        String sentence = "";
        while ((line2read = reader.readLine()) != null) {
            if (line2read.equals("")) //sentence break
            {
                counter++;
                if (counter % 100000 == 0)
                    System.out.print(counter);
                else if (counter % 10000 == 0)
                    System.out.print(".");
                sentences.add(sentence);
                sentence = "";
            } else
                sentence+=line2read+'\n';
        }
        System.out.print("\n");
        return sentences;
    }

    public static <T> void write(T o, String filePath) throws IOException {
        FileOutputStream fos = new FileOutputStream(filePath);
        GZIPOutputStream gz = new GZIPOutputStream(fos);
        ObjectOutput writer = new ObjectOutputStream(gz);
        writer.writeObject(o);
        writer.close();
    }

    public static <T> T load(String filePath) throws Exception {
        FileInputStream fis = new FileInputStream(filePath);
        GZIPInputStream gz = new GZIPInputStream(fis);
        ObjectInput reader = new ObjectInputStream(gz);
        return (T) reader.readObject();
    }
}