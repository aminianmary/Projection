/**
 * Created by Maryam Aminian on 10/17/16.
 */
import SentenceStruct.*;
import Structures.IndexMap;
import util.IO;

import java.io.*;
import java.util.*;

public class Projection {
    public static void main(String args[]) throws Exception {
        //args[0]: source file with semantic roles in the conll09 format (input)
        //args[1]: target file (each sentence in a separate line) (input)
        //args[4]: alignment file (input)
        //args[5]: projected file (output)

        String sourceFile = args[0]; //source file has supervised SRL
        String targetFile = args[1]; //target file has supervised/gold SRL (for comparing purposes)
        String alignmentFile = args[2];
        String projectedTargetFile = args[3];
        String sourceClusterFilePath = args[4];
        String targetClusterFilePath = args[5];
        double sentenceTrainingGain = Double.parseDouble(args[6]);
        String projectionFilters = args[7];  // PKF (pos kind filter)

        BufferedWriter projectedFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile + ".ids"), "UTF-8"));

        Alignment alignment = new Alignment(alignmentFile);
        HashMap<Integer, HashMap<Integer, Integer>> alignmentDic = alignment.getSourceTargetAlignmentDic();

        final IndexMap sourceIndexMap = new IndexMap(sourceFile, sourceClusterFilePath);
        final IndexMap targetIndexMap = new IndexMap(targetFile, targetClusterFilePath);

        ArrayList<String> sourceSents = IO.readCoNLLFile(sourceFile);
        ArrayList<String> targetSents = IO.readCoNLLFile(targetFile);
        int numOfProjectedSentence = 0;
        int projectedSentencesSizeSum = 0;
        int tAvgSentenceLength = 0;
        int numOfTrainingInstances= 0;
        System.out.println("Projection started...");

        for (int senId = 0; senId < sourceSents.size(); senId++) {
            if (senId % 100000 == 0)
                System.out.print(senId);
            else if (senId % 10000 == 0)
                System.out.print(".");

            Sentence sourceSen = new Sentence(sourceSents.get(senId), sourceIndexMap);
            Sentence targetSen = new Sentence(targetSents.get(senId), targetIndexMap);

            tAvgSentenceLength += targetSen.getLength();
            Object[] projectionOutput = project(sourceSen, targetSen, alignmentDic.get(senId),
                    sourceIndexMap, targetIndexMap, projectionFilters);

            HashMap<Integer, String> projectedPreds = (HashMap<Integer, String>) projectionOutput[0];
            TreeMap<Integer, TreeMap<Integer, String>> projectedArgs = (TreeMap<Integer, TreeMap<Integer, String>>) projectionOutput[1];
            numOfTrainingInstances = (Integer) projectionOutput[2];

            double trainGainPerWord = (double) numOfTrainingInstances / targetSen.getLength();

            if (trainGainPerWord >= sentenceTrainingGain) {
                numOfProjectedSentence++;
                projectedSentencesSizeSum += targetSen.getLength();
                projectedSentencesIDWriter.write(senId + "\n");
                writeProjectedRoles(getSentenceForOutput(targetSents.get(senId)), projectedPreds, projectedArgs, projectedFileWriter);
            }
        }
        System.out.print(sourceSents.size() + "\n");
        System.out.println("Number of projected sentences " +
                numOfProjectedSentence + "/" + targetSents.size() + " (" + ((double) numOfProjectedSentence / targetSents.size()) * 100 + "%)");
        System.out.println("Average length of target sentences: " + (double) tAvgSentenceLength / targetSents.size());
        System.out.println("Average length of projected sentences: " + (double) projectedSentencesSizeSum / numOfProjectedSentence);
        System.out.println("Number of training instances in the projected data " + numOfTrainingInstances);
        projectedFileWriter.flush();
        projectedFileWriter.close();
        projectedSentencesIDWriter.flush();
        projectedSentencesIDWriter.close();
    }

    public static Object[] project(Sentence sourceSent, Sentence targetSent, HashMap<Integer, Integer> alignmentDic,
                                   IndexMap sourceIndexMap, IndexMap targetIndexMap, String projectionFilters) throws Exception {
        HashMap<Integer, simplePA> sourceSimplePAMap = sourceSent.getSimplePAMap();
        int[] sourcePosTags = sourceSent.getPosTags();
        int[] targetPosTags = targetSent.getPosTags();
        int numOfTrainingInstances =0;

        HashMap<Integer, String> projectedPreds = new HashMap<>();
        TreeMap<Integer, TreeMap<Integer, String>> projectedArgs = new TreeMap<>();

        for (int sourcePIdx : sourceSimplePAMap.keySet()) {
            simplePA sspa = sourceSimplePAMap.get(sourcePIdx);

            if (alignmentDic.containsKey(sourcePIdx)) {
                int targetPIdx = alignmentDic.get(sourcePIdx);
                String p1 = sourceIndexMap.int2str(sourcePosTags[sourcePIdx]);
                String p2 = targetIndexMap.int2str(targetPosTags[targetPIdx]);
                if (pFilter(projectionFilters, p1, p2)) {
                    //project predicate label
                    projectedPreds.put(targetPIdx, sspa.getPredicateLabel());
                    HashMap<Integer, String> sourceArgs = sspa.getArgumentLabels();

                    for (int swi = 1; swi < sourceSent.getLength(); swi++) {
                        if (alignmentDic.containsKey(swi)) {
                            int twi = alignmentDic.get(swi);
                            String o1 = sourceIndexMap.int2str(sourcePosTags[swi]);
                            String o2 = targetIndexMap.int2str(targetPosTags[twi]);
                            if (pFilter(projectionFilters, o1, o2)) {
                                //project word label (either NULL or argument)
                                String twl = "_";
                                if (sourceArgs.containsKey(swi))
                                    twl = sourceArgs.get(swi);

                                numOfTrainingInstances ++;
                                if (!projectedArgs.containsKey(twi)) {
                                    TreeMap<Integer, String> temp = new TreeMap<>();
                                    temp.put(targetPIdx, twl);
                                    projectedArgs.put(twi, temp);
                                } else if (!projectedArgs.get(twi).containsKey(targetPIdx))
                                    projectedArgs.get(twi).put(targetPIdx, twl);
                            }
                        }
                    }
                }
            }
        }

        //project source non-predicate words
        for (int swi = 1; swi < sourceSent.getLength(); swi++){
            if (!sourceSimplePAMap.containsKey(swi)){
                //not a predicate in the source sentence
                if (alignmentDic.containsKey(swi)) {
                    int twi = alignmentDic.get(swi);
                    String o1 = sourceIndexMap.int2str(sourcePosTags[swi]);
                    String o2 = targetIndexMap.int2str(targetPosTags[twi]);
                    if (pFilter(projectionFilters, o1, o2)){
                        //project "_" label from source to target
                        if (!projectedPreds.containsKey(twi))
                            projectedPreds.put(twi, "_");
                        else
                            System.out.print("ERROR!! Target word is aligned to multiple source words!");
                    }
                }
            }

        }


        return new Object[]{projectedPreds, projectedArgs, numOfTrainingInstances};
    }


    public static void writeProjectedRoles(ArrayList<String> targetWords,
                                           HashMap<Integer, String> projectedPreds,
                                           TreeMap<Integer, TreeMap<Integer, String>> projectedArgs,
                                           BufferedWriter projectedFileWriter) throws IOException {
        ArrayList<Integer> targetPIndices = new ArrayList<>(projectedPreds.keySet());
        Collections.sort(targetPIndices);
        int wordIndex = 0;
        for (String word : targetWords) {
            wordIndex++;
            projectedFileWriter.write(word);
            //write projected predicates
            if (targetPIndices.contains(wordIndex)) {
                if (!projectedPreds.get(wordIndex).equals("_"))
                    projectedFileWriter.write("\tY\t" + projectedPreds.get(wordIndex));
                else
                    projectedFileWriter.write("\t_\t" + projectedPreds.get(wordIndex));
            }else
                projectedFileWriter.write("\t?\t?");

            //write projected arguments
            if (projectedArgs.containsKey(wordIndex)) {
                for (int pIndex : targetPIndices) {
                    if (!projectedPreds.get(pIndex).equals("_"))
                        if (projectedArgs.get(wordIndex).containsKey(pIndex))
                            projectedFileWriter.write("\t" + projectedArgs.get(wordIndex).get(pIndex));
                        else
                            projectedFileWriter.write("\t?");
                }
            } else
                for (int pIndex : targetPIndices)
                    if (!projectedPreds.get(pIndex).equals("_"))
                        projectedFileWriter.write("\t?");
            projectedFileWriter.write("\n");

        }
        projectedFileWriter.write("\n");
    }

    public static ArrayList<String> getSentenceForOutput(String sentenceInCoNLLFormat) {
        String[] lines = sentenceInCoNLLFormat.split("\n");
        ArrayList<String> sentenceForOutput = new ArrayList<String>();
        for (String line : lines) {
            String[] fields = line.split("\t");
            String filedsForOutput = "";
            //we just need the first 12 fields. The rest of filed must be filled based on what system predicted
            for (int k = 0; k < 12; k++)
                filedsForOutput += fields[k] + "\t";
            sentenceForOutput.add(filedsForOutput.trim());
        }
        return sentenceForOutput;
    }


    public static boolean samePosKind (String p1, String p2){

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

    public static boolean pFilter (String projectionFilters, String p1, String p2){
        if (projectionFilters.contains("PF") || projectionFilters.contains("pf"))
            return samePosKind(p1, p2);
        else
            return true;
    }

}