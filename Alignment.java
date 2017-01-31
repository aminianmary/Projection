import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by Maryam Aminian on 10/17/16.
 */
public class Alignment {
    private HashMap<Integer, HashMap<Integer, Integer>> sourceTargetAlignmentDic;
    private HashMap<Integer, HashMap<Integer, Integer>> targetSourceAlignmentDic;

    public Alignment(String alignmentFilePath) throws IOException {
        Object[] obj = createAlignmentDic(alignmentFilePath);
        sourceTargetAlignmentDic = (HashMap<Integer, HashMap<Integer, Integer>>) obj[0];
        targetSourceAlignmentDic = (HashMap<Integer, HashMap<Integer, Integer>>) obj[1];
    }

    public HashMap<Integer, HashMap<Integer, Integer>> getSourceTargetAlignmentDic() {
        return sourceTargetAlignmentDic;
    }

    public HashMap<Integer, HashMap<Integer, Integer>> getTargetSourceAlignmentDic() {
        return targetSourceAlignmentDic;
    }

    public Object[] createAlignmentDic
            (String alignmentFile) throws IOException {
        System.out.println("Getting alignment dictionaries...");
        BufferedReader alignmentReader = new BufferedReader(new FileReader(alignmentFile));
        HashMap<Integer, HashMap<Integer, Integer>> sourceTargetAlignDic = new HashMap<Integer, HashMap<Integer, Integer>>();
        HashMap<Integer, HashMap<Integer, Integer>> targetSourceAlignDic = new HashMap<Integer, HashMap<Integer, Integer>>();
        String alignLine2Read = "";
        int sentenceID = -1;

        while (((alignLine2Read = alignmentReader.readLine()) != null)) {
            sentenceID++;
            if (sentenceID % 100000 == 0)
                System.out.print(sentenceID);
            else if (sentenceID % 10000 == 0)
                System.out.print(".");

            sourceTargetAlignDic.put(sentenceID, new HashMap<Integer, Integer>());
            targetSourceAlignDic.put(sentenceID, new HashMap<Integer, Integer>());

            if (!alignLine2Read.trim().equals("")) {
                String[] alignWords = alignLine2Read.split(" ");

                for (int i = 0; i < alignWords.length; i++) {
                    //indices from 1
                    Integer sourceIndex = Integer.parseInt(alignWords[i].toString().split("-")[0]);
                    Integer targetIndex = Integer.parseInt(alignWords[i].split("-")[1]);

                    if (!sourceTargetAlignDic.get(sentenceID).containsKey(sourceIndex))
                        sourceTargetAlignDic.get(sentenceID).put(sourceIndex, targetIndex);
                    else {
                        //removes the noisy alignment
                        sourceTargetAlignDic.get(sentenceID).remove(sourceIndex);
                        //System.out.println("SentenceStruct: " + sentenceID + " source index: " + sourceIndex + " is aligned to multiple target words");
                    }

                    if (!targetSourceAlignDic.get(sentenceID).containsKey(targetIndex))
                        targetSourceAlignDic.get(sentenceID).put(targetIndex, sourceIndex);
                }

            } else {
                //empty alignment
                if (alignLine2Read.equals("")) {
                    //System.out.println("SentenceStruct " + sentenceID + ": alignment is empty");
                }
            }
        }
        System.out.print(sentenceID+"\nDONE!\n");
        alignmentReader.close();
        return new Object[]{sourceTargetAlignDic, targetSourceAlignDic};
    }

    public HashSet<Integer> getTargetWordsWithAlignment4ThisSentence (int senID){
        return new HashSet<>(targetSourceAlignmentDic.get(senID).keySet());
    }
}
