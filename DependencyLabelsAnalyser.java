import SentenceStruct.Sentence;
import SentenceStruct.simplePA;
import Structures.IndexMap;
import util.IO;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * Created by Maryam Aminian on 3/28/17.
 */
public class DependencyLabelsAnalyser {
    HashMap<Integer, HashMap<Integer, Integer>> matchConfusionMatrix;
    HashMap<Integer, HashMap<Integer, Integer>> totalConfusionMatrix;


    public DependencyLabelsAnalyser(){
        matchConfusionMatrix = new HashMap<>();
        totalConfusionMatrix = new HashMap<>();
    }

    public static void main(String args[]) throws Exception {
        //args[0]: source file with semantic roles in the conll09 format (input)
        //args[1]: target file with semantic roles in the conll09 format (input)
        //args[4]: alignment file (input)
        //args[5]: projected file (output)

        String sourceFile = args[0]; //source file has supervised SRL tags
        String targetFile = args[1]; //target file has supervised SRL tags
        String alignmentFile = args[2];
        String sourceClusterFilePath = args[3];
        String targetClusterFilePath = args[4];
        String projectionFilters = args[5];
        double sparsityThreshold = Double.parseDouble(args[6]);

        Alignment alignment = new Alignment(alignmentFile);
        HashMap<Integer, HashMap<Integer, Integer>> alignmentDic = alignment.getSourceTargetAlignmentDic();

        final IndexMap sourceIndexMap = new IndexMap(sourceFile, sourceClusterFilePath);
        final IndexMap targetIndexMap = new IndexMap(targetFile, targetClusterFilePath);
        ArrayList<String> sourceSents = IO.readCoNLLFile(sourceFile);
        ArrayList<String> targetSents = IO.readCoNLLFile(targetFile);

        System.out.println("Projection started...");
        DependencyLabelsAnalyser dla = new DependencyLabelsAnalyser();
        for (int senId = 0; senId < sourceSents.size(); senId++) {
            if (senId % 100000 == 0)
                System.out.print(senId);
            else if (senId % 10000 == 0)
                System.out.print(".");

            Sentence sourceSen = new Sentence(sourceSents.get(senId), sourceIndexMap);
            Sentence targetSen = new Sentence(targetSents.get(senId), targetIndexMap);
            int maxNumOfProjectedLabels = dla.getNumOfProjectedLabels(sourceSen, alignmentDic.get(senId));
            double trainGainPerWord = (double) maxNumOfProjectedLabels/targetSen.getLength();

            if (trainGainPerWord >= sparsityThreshold){
                dla.analysSourceTargetDependencyMatch(sourceSen, targetSen, alignmentDic.get(senId),
                        sourceIndexMap, targetIndexMap, projectionFilters, sparsityThreshold);
            }
        }
        System.out.print(sourceSents.size() + "\n");
        dla.writeConfusionMatrix("ConfusionMatrix.out", sourceIndexMap, targetIndexMap);
    }

    public void analysSourceTargetDependencyMatch(Sentence sourceSent, Sentence targetSent,
                                                  HashMap<Integer, Integer> alignmentDic,
                                                  IndexMap sourceIndexMap, IndexMap targetIndexMap,
                                                  String projectionFilters, double sparsityThreshold)
            throws Exception {

        HashMap<Integer, simplePA> sourcePAMap = sourceSent.getSimplePAMap();
        HashMap<Integer, simplePA> targetPAMap = sourceSent.getSimplePAMap();

        int[] sourcePosTags = sourceSent.getPosTags();
        int[] targetPosTags = targetSent.getPosTags();
        int[] sourceDepLabels = sourceSent.getDepLabels();
        int[] targetDepLabels = targetSent.getDepLabels();

        for (int sourcePIdx : sourcePAMap.keySet()) {
            simplePA sspa = sourcePAMap.get(sourcePIdx);
            HashMap<Integer, String> sourceArgs = sspa.getArgumentLabels();

            if (alignmentDic.containsKey(sourcePIdx)) {
                int targetPIdx = alignmentDic.get(sourcePIdx);

                String p1 = sourceIndexMap.int2str(sourcePosTags[sourcePIdx]);
                String p2 = targetIndexMap.int2str(targetPosTags[targetPIdx]);
                int d1 = sourceDepLabels[sourcePIdx];
                int d2 = targetDepLabels[targetPIdx];
                HashMap<Integer, String> targetArgs = new HashMap<>();

                if (posFilter(projectionFilters, p1, p2)) {
                    updateTotalConfusionMatrix(d1, d2);
                    //projection performed
                    if (targetPAMap.containsKey(targetPIdx)) {
                        //superAMap.containsKey(targetPIdx)) {
                        //supervised SRL has labeled this word as a predicate in target side --> correct label
                        updateMatchConfusionMatrix(d1, d2);
                        simplePA tspa = targetPAMap.get(targetPIdx);
                        targetArgs = tspa.getArgumentLabels();

                        for (int swi = 1; swi < sourceSent.getLength(); swi++) {
                            if (alignmentDic.containsKey(swi)) {
                                int twi = alignmentDic.get(swi);
                                String o1 = sourceIndexMap.int2str(sourcePosTags[swi]);
                                String o2 = targetIndexMap.int2str(targetPosTags[twi]);
                                int k1 = sourceDepLabels[swi];
                                int k2 = targetDepLabels[twi];

                                if (posFilter(projectionFilters, o1, o2)) {
                                    updateTotalConfusionMatrix(k1, k2);
                                    String sourceLabel = sourceArgs.containsKey(swi) ? sourceArgs.get(swi) : "_";
                                    String targetLabel = targetArgs.containsKey(twi)? targetArgs.get(twi) : "_";

                                    if (sourceLabel.equals(targetLabel))
                                        updateMatchConfusionMatrix(k1, k2);
                                }
                            }
                        }
                    }
                }
            }
        }

        //project source non-predicate words
        for (int swi = 1; swi < sourceSent.getLength(); swi++){
            if (!sourcePAMap.containsKey(swi)){
                //not a predicate in the source sentence
                if (alignmentDic.containsKey(swi)) {
                    int twi = alignmentDic.get(swi);
                    String o1 = sourceIndexMap.int2str(sourcePosTags[swi]);
                    String o2 = targetIndexMap.int2str(targetPosTags[twi]);
                    int d1 = sourceDepLabels[swi];
                    int d2 = targetDepLabels[twi];

                    if (posFilter(projectionFilters, o1, o2) ){
                        updateTotalConfusionMatrix(d1, d2);

                        if (!targetPAMap.containsKey(twi))
                            updateMatchConfusionMatrix(d1, d2);
                    }
                }
            }

        }
    }

    public boolean samePosSoft(String p1, String p2){

        if (p1.equals(p2))
            return true;

        if (p1.equals("PRON"))
            if (p2.equals("DET") || p2.equals("NOUN") || p2.equals("ADJ"))
                return true;
        if (p1.equals("NOUN"))
            if (p2.equals("DET") || p2.equals("PRON"))
                return true;
        if (p1.equals("DET"))
            if (p2.equals("NOUN") || p2.equals("PRON") || p2.equals("NUM"))
                return true;
        if (p1.equals("PRT"))
            if (p2.equals("ADV"))
                return true;
        if (p1.equals("ADV"))
            if (p2.equals("PRT") || p2.equals("ADJ"))
                return true;
        if (p1.equals("ADJ"))
            if (p2.equals("ADV") || p2.equals("PRON"))
                return true;
        if (p1.equals("X"))
            if (p2.equals("NUM") || p2.equals("."))
                return true;
        if (p1.equals("."))
            if (p2.equals("X"))
                return true;
        if (p1.equals("NUM"))
            if (p2.equals("X") || p2.equals("DET"))
                return true;

        return false;
    }

    public boolean samePosExact (String p1, String p2){

        if (p1.equals(p2))
            return true;
        return false;
    }

    public boolean posFilter(String projectionFilters, String p1, String p2){
        if (projectionFilters.contains("PS") || projectionFilters.contains("ps"))
            return samePosSoft(p1, p2);
        else if (projectionFilters.contains("PE") || projectionFilters.contains("pe"))
            return samePosExact(p1, p2);
        else
            return true;
    }

    public void updateMatchConfusionMatrix(int sourceDep, int targetDep){
        if (!matchConfusionMatrix.containsKey(sourceDep)){
            HashMap<Integer, Integer> t = new HashMap<>();
            t.put(targetDep,1);
            matchConfusionMatrix.put(sourceDep, t);
        }else if (!matchConfusionMatrix.get(sourceDep).containsKey(targetDep)){
            matchConfusionMatrix.get(sourceDep).put(targetDep, 1);
        }else
            matchConfusionMatrix.get(sourceDep).put(targetDep, matchConfusionMatrix.get(sourceDep).get(targetDep) +1);
    }

    public void updateTotalConfusionMatrix (int sourceDep, int targetDep){
        if (!totalConfusionMatrix.containsKey(sourceDep)){
            HashMap<Integer, Integer> t = new HashMap<>();
            t.put(targetDep,1);
            totalConfusionMatrix.put(sourceDep, t);
        }else if (!totalConfusionMatrix.get(sourceDep).containsKey(targetDep)){
            totalConfusionMatrix.get(sourceDep).put(targetDep, 1);
        }else
            totalConfusionMatrix.get(sourceDep).put(targetDep, totalConfusionMatrix.get(sourceDep).get(targetDep) +1);
    }

    public void writeConfusionMatrix (String outputFile, IndexMap sourceIndexMap, IndexMap targetIndexMap)
            throws Exception{

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile)));
        ArrayList<String> sourceDepLabelSet = new ArrayList<String> (sourceIndexMap.getDepLabes());
        ArrayList<String> targetDepLabelSet = new ArrayList<String>  (targetIndexMap.getDepLabes());

        for (String sd : sourceDepLabelSet){
            int sd_index = sourceIndexMap.str2int(sd);

            for (String td : targetDepLabelSet){
                int td_index = targetIndexMap.str2int(td);
                int match = 0;
                int total = 0;
                if (matchConfusionMatrix.containsKey(sd_index) && matchConfusionMatrix.get(sd_index).containsKey(td_index))
                    match = matchConfusionMatrix.get(sd_index).get(td_index);
                if (totalConfusionMatrix.containsKey(sd_index) && totalConfusionMatrix.get(sd_index).containsKey(td_index))
                    total = totalConfusionMatrix.get(sd_index).get(td_index);

                double matchPercentage = 0;
                if (total != 0)
                    matchPercentage = (double) match/total;

                writer.write(sd + "-"+ td +"\t"+total+"\t"+ matchPercentage+"\n");
            }
        }
        writer.flush();
        writer.close();
    }

    public int getNumOfProjectedLabels (Sentence source, HashMap<Integer, Integer> alignmentDic){
        HashSet<Integer> argIndices = source.getArgIndices();
        int numOfProjectedLabels =0;
        for (int arg: argIndices){
            if (alignmentDic.containsKey(arg))
              numOfProjectedLabels++;
        }
        return numOfProjectedLabels;
    }
}
