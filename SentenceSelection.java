import SentenceStruct.Sentence;
import Structures.IndexMap;
import util.IO;

import java.io.*;

/**
 * Created by Maryam Aminian on 2/23/17.
 */
public class SentenceSelection {
    public static void main(String[] args) {
        String inputCoNLLFile = args[0];
        String clusterFile = args[1];
        String outputSelectedFile = args[2];
        double completenessThreshold = Double.parseDouble(args[3]);

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputCoNLLFile)));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputSelectedFile)));
            BufferedWriter idwriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputSelectedFile+".ids")));
            IndexMap indexMap = new IndexMap(inputCoNLLFile, clusterFile);

            String line2read = "";
            String sentence = "";
            int sentenceID = -1;
            int numOfTargetSentence = 0;
            int targetSentencesSizeSum =0;

            while ((line2read = reader.readLine()) != null) {
                if (line2read.equals("")) {
                    //new sentence
                    sentenceID++;
                    Sentence sen = new Sentence(sentence, indexMap);
                    if (sentenceID%1000 ==0)
                        System.out.print(sentenceID+"\n");

                    if (sen.getCompletenessDegree() >= completenessThreshold) {
                        //complete sentence
                        writer.write(sentence + "\n");
                        idwriter.write(sentenceID + "\n");
                        numOfTargetSentence ++;
                        targetSentencesSizeSum += sen.getLength();
                    }
                    sentence = "";
                } else {
                    sentence += line2read.trim() + "\n";
                }
            }
            writer.flush();
            writer.close();
            idwriter.flush();
            idwriter.close();
            System.out.print("Number of selected sentences: "+ numOfTargetSentence+"\n");
            System.out.print("Average length of selected sentences: "+ (double) targetSentencesSizeSum/numOfTargetSentence+"\n");

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
