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
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(coNLLFile)));
        ArrayList<String> sentences = new ArrayList<String>();
        String line2read = "";
        int counter = 0;
        StringBuilder sentence = new StringBuilder();
        while ((line2read = reader.readLine()) != null) {
            if (line2read.equals("")) //sentence break
            {
                counter++;

                if (counter % 100000 == 0)
                    System.out.print(counter);
                else if (counter % 10000 == 0)
                    System.out.print(".");

                String senText = sentence.toString().trim();
                if (senText.length() > 0)
                    sentences.add(senText);
                sentence = new StringBuilder();
            } else {
                sentence.append(line2read);
                sentence.append("\n");
            }
        }
        return sentences;
    }
}