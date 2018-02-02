import SentenceStruct.Sentence;
import SentenceStruct.simplePA;
import Structures.IndexMap;
import Structures.Pair;
import util.IO;

import javax.swing.tree.TreeNode;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * Created by Maryam Aminian on 3/28/17.
 */
public class SRLMatchAnalyser {


    public static void main(String args[]) throws Exception {

        String projFile = args[0]; //target file has supervised SRL tags
        String supervisedFile = args[1];
        String clusterFilePath = args[2];
        final IndexMap targetIndexMap = new IndexMap(projFile, clusterFilePath);
        ArrayList<String> projSents = IO.readCoNLLFile(projFile);
        ArrayList<String> superSents = IO.readCoNLLFile(supervisedFile);

        double match_0_50 =0;
        double match_50_100 =0;
        //double match_60_80 =0;
        //double match_80_100 =0;

        double total_0_50 =0;
        double total_50_100 =0;
        //double total_60_80 =0;
        //double total_80_100 =0;


        for (int senId = 0; senId < projSents.size(); senId++) {
            if (senId % 100000 == 0)
                System.out.print(senId);
            else if (senId % 10000 == 0)
                System.out.print(".");

            Sentence targetSen = new Sentence(projSents.get(senId), targetIndexMap);
            Sentence targetSuperSen = new Sentence(superSents.get(senId), targetIndexMap);
            Object[] stats = getSRLMatch(targetSen, targetSuperSen);
            int matchSemDep = (int) stats[0];
            int totalSemDep = (int) stats[1];
            int numOfWordWOAlignment = (int) stats[2];
            //double trainGainPerWord = (double) (targetSen.getLength() - numOfWordWOAlignment)/targetSen.getLength();
            double trainGainPerWord = targetSen.getCompletenessDegree();

            if (trainGainPerWord <= 0.5){
                match_0_50 += matchSemDep;
                total_0_50 += totalSemDep;

            }else if (0.5 < trainGainPerWord){
                match_50_100 += matchSemDep;
                total_50_100 += totalSemDep;
            }
            /*
            else if (0.6 < trainGainPerWord &&trainGainPerWord <= 0.8){
                match_60_80 += matchSemDep;
                total_60_80+= totalSemDep;
            }else if (trainGainPerWord > 0.8)
            {
                match_80_100 += matchSemDep;
                total_80_100 += totalSemDep;
            }
            */
        }

        System.out.print("acc less than 50: "+ (double) match_0_50/total_0_50+"\n");
        System.out.print("acc between 50 and 100: "+ (double) match_50_100/total_50_100+"\n");
        //System.out.print("acc between 60 and 80: "+ (double) match_60_80/total_60_80+"\n");
        //System.out.print("acc larger than 80: "+ (double) match_80_100/total_80_100+"\n");

    }

    public static Object[] getSRLMatch (Sentence projSent, Sentence superSent)
            throws Exception {

        int numOfMatchedSemanticDependencies = 0;
        int numOfProjectedSemanticDependecies =0;
        HashMap<Integer, simplePA> projPAMap = projSent.getSimplePAMap();
        HashMap<Integer, simplePA> goldPAMap = superSent.getSimplePAMap();
        HashMap<Integer, HashSet<Integer>> undecidedArgs =projSent.getUndecidedArgs();
        TreeSet<Integer> projPredicateIndices = new TreeSet<> (projPAMap.keySet());
        String[] fillPreds= projSent.getFillPredicate();

        for (int projPIdx : projPredicateIndices) {
            simplePA pspa = projPAMap.get(projPIdx);
            HashMap<Integer, String> projArgs = pspa.getArgumentLabels();
            numOfProjectedSemanticDependecies++;

            if (goldPAMap.containsKey(projPIdx)) {
                numOfMatchedSemanticDependencies++;
                HashMap<Integer, String> goldArgs = goldPAMap.get(projPIdx).getArgumentLabels();

                for (int w = 1; w < projSent.getLength(); w++) {
                    String projLabel = "_";
                    String superLabel = "_";

                    if (projArgs.containsKey(w))
                        projLabel = projArgs.get(w);
                    else if (fillPreds[w].equals("?"))
                        projLabel = "?";

                    if (goldArgs.containsKey(w))
                        superLabel = goldArgs.get(w);

                    if (!projLabel.equals("?")) {
                        numOfProjectedSemanticDependecies++;
                        if (projLabel.equals(superLabel)) {
                            numOfMatchedSemanticDependencies++;
                        }
                    }
                }
            }
        }
        //project source non-predicate words
        for (int swi = 1; swi < projSent.getLength(); swi++) {
            if (!projPAMap.containsKey(swi) && !fillPreds[swi].equals("?"))
            {
                numOfProjectedSemanticDependecies++;
                if (!goldPAMap.containsKey(swi))
                        numOfMatchedSemanticDependencies++;
            }
        }

        return new Object[]{numOfMatchedSemanticDependencies,
                numOfProjectedSemanticDependecies, undecidedArgs.size()};
    }

}
