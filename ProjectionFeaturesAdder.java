import SentenceStruct.Sentence;
import Structures.IndexMap;
import Structures.Pair;
import util.IO;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * Created by Maryam Aminian on 3/10/17.
 */
public class ProjectionFeaturesAdder {

    public static void main(String[] args) {
        String input = args[0];
        String clusterFile = args[1];
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(input+"_w_projection_feat")));
            IndexMap indexMap = new IndexMap(input, clusterFile);
            ArrayList<String> sentences = IO.readCoNLLFile(input);
            for (String sen : sentences) {
                Pair originalSentenceFields = getOriginalFields(sen);

                ArrayList<String> fieldsTo12 = (ArrayList<String>) originalSentenceFields.first;
                ArrayList<String> fieldsFrom13 = (ArrayList<String>) originalSentenceFields.second;

                Sentence sentence = new Sentence(sen, indexMap);
                int[] posTags = sentence.getPosTags();
                int[] depHeads = sentence.getDepHeads();
                int[] depLabels = sentence.getDepLabels();

                for (int i=0; i< sentence.getLength()-1; i++) {
                    int wordIndex = i+1;
                    writer.write(fieldsTo12.get(i)+"\t");
                    writer.write(indexMap.int2str(posTags[wordIndex])+"\t");
                    writer.write(indexMap.int2str(posTags[depHeads[wordIndex]])+"\t");
                    writer.write(indexMap.int2str(depLabels[wordIndex])+"\t");
                    writer.write(fieldsFrom13.get(i)+"\n");
                }
                writer.write("\n");
            }

            writer.flush();
            writer.close();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static Pair getOriginalFields(String sentenceInCoNLLFormat) {
        String[] lines = sentenceInCoNLLFormat.split("\n");
        ArrayList<String> fieldsTo12 = new ArrayList<String>();
        ArrayList<String> fieldsFrom13 = new ArrayList<String>();
        for (String line : lines) {
            String[] fields = line.split("\t");
            String fields1 = "";
            String fields2 = "";

            for (int k = 0; k < 12; k++)
                fields1 += fields[k] + "\t";
            fieldsTo12.add(fields1.trim());

            for (int k = 12; k < fields.length; k++)
                fields2 += fields[k] + "\t";
            fieldsFrom13.add(fields2.trim());
        }
        return new Pair<>(fieldsTo12, fieldsFrom13);
    }
}
