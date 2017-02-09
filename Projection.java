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

        BufferedWriter projectedFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile),"UTF-8"));
        BufferedWriter projectedSentencesIDWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+".ids"),"UTF-8"));

        Alignment alignment = new Alignment(alignmentFile);
        HashMap<Integer, HashMap<Integer, Integer>> alignmentDic = alignment.getSourceTargetAlignmentDic();

        final IndexMap sourceIndexMap = new IndexMap(sourceFile, sourceClusterFilePath);
        final IndexMap targetIndexMap = new IndexMap(targetFile, targetClusterFilePath);

        ArrayList<String> sourceSents = IO.readCoNLLFile(sourceFile);
        ArrayList<String> targetSents = IO.readCoNLLFile(targetFile);
        int numOfProjectedSentence =0;
        int projectedSentencesSizeSum = 0;
        int numOfTrainingInstances = 0;
        int targetAvgSentenceLength = 0;
        System.out.println("Projection started...");

        for (int senId = 0; senId < sourceSents.size(); senId++) {
            if (senId % 100000 == 0)
                System.out.print(senId);
            else if (senId % 10000 == 0)
                System.out.print(".");
            Sentence sourceSen = new Sentence(sourceSents.get(senId), sourceIndexMap);
            Sentence targetSen = new Sentence(targetSents.get(senId), targetIndexMap);
            HashSet<Integer> targetWordsWithoutAlignment = getTargetIndicesWithoutAlignment(
                    alignment.getTargetWordsWithAlignment4ThisSentence(senId), targetSen.getLength());

            targetAvgSentenceLength += targetSen.getLength();
            Object[] projectionOutput = project(sourceSen, targetSen, alignmentDic.get(senId), sourceIndexMap, targetIndexMap);
            HashMap<Integer, String> projectedPIndices = (HashMap<Integer, String>) projectionOutput[0];
            TreeMap<Integer, TreeMap<Integer, String>> projectedArgIndices = (TreeMap<Integer, TreeMap<Integer, String>>) projectionOutput[1];
            //assert none of target words without alignment are in the projection outputs
            if (foundUndecidedTargetWordsInProjectionOutputs(projectedPIndices.keySet(), projectedArgIndices.keySet(),
                    targetWordsWithoutAlignment))
                System.out.print("\n**NOTE** Undecided target words found in the projection outputs --> Sentence " + senId + "\n");

            //check number of training instances that sentence provides
            int numOfDecidedTrainingInstancesSentenceProvides = 0;
            int totalNumOfTrainingInstancesSentenceProvides = projectedPIndices.size() * targetSen.getLength();

            for (int argIdx: projectedArgIndices.keySet())
                numOfDecidedTrainingInstancesSentenceProvides += projectedArgIndices.get(argIdx).keySet().size();

            double trainingGain = (double) numOfDecidedTrainingInstancesSentenceProvides/totalNumOfTrainingInstancesSentenceProvides;
            double ratioOfSourceAlignedWords = (double) alignmentDic.get(senId).size() / sourceSen.getLength();
            double trainGainPerWord = (double) numOfDecidedTrainingInstancesSentenceProvides/targetSen.getLength();
            //if (trainingGain >= sentenceTrainingGain || numOfDecidedTrainingInstancesSentenceProvides >= 10){
            //if (ratioOfSourceAlignedWords >= 0.99){
            if (trainGainPerWord >= sentenceTrainingGain){
                System.out.println("**INFO** Sentence training gain: "+ trainingGain +" Number of training instances: "+numOfDecidedTrainingInstancesSentenceProvides+" --> included in the projected sentences");
                numOfProjectedSentence++;
                projectedSentencesSizeSum += targetSen.getLength();
                numOfTrainingInstances += numOfDecidedTrainingInstancesSentenceProvides;
                projectedSentencesIDWriter.write(senId + "\n");
                writeProjectedRoles(getSentenceForOutput(targetSents.get(senId)), targetWordsWithoutAlignment,
                        projectedPIndices, projectedArgIndices, projectedFileWriter);
            }else
                System.out.println("**INFO** Sentence training gain: "+ trainingGain +" Number of training instances: "+numOfDecidedTrainingInstancesSentenceProvides+" --> excluded from projected sentences");
        }
        System.out.print(sourceSents.size()+"\n");
        System.out.println("Number of projected sentences "+
                numOfProjectedSentence+"/"+targetSents.size() +" ("+((double) numOfProjectedSentence/targetSents.size())*100+"%)");
        System.out.println("Average length of target sentences: "+ (double) targetAvgSentenceLength/targetSents.size());
        System.out.println("Average length of projected sentences: " + (double) projectedSentencesSizeSum/numOfProjectedSentence);
        System.out.println("Number of training instances in the projected data "+ numOfTrainingInstances);
        projectedFileWriter.flush();
        projectedFileWriter.close();
        projectedSentencesIDWriter.flush();
        projectedSentencesIDWriter.close();
    }

    public static Object[] project(Sentence sourceSent, Sentence targetSent, HashMap<Integer, Integer> alignmentDic,
                                   IndexMap sourceIndexMap, IndexMap targetIndexMap) throws Exception {
        ArrayList<PA> sourcePAs = sourceSent.getPredicateArguments().getPredicateArgumentsAsArray();
        ArrayList<PA> targetPAs = new ArrayList<>();
        HashMap<Integer, String> projectedPIndices = new HashMap<>();
        TreeMap<Integer, TreeMap<Integer, String>> projectedArgIndices = new TreeMap<>();

        for (PA pa : sourcePAs) {
            int sourcePIdx = pa.getPredicate().getIndex();

            if (alignmentDic.containsKey(sourcePIdx)) {
                int targetPIdx = alignmentDic.get(sourcePIdx);

                //check if aligned target word is not a punctuation
                if (!targetSent.isPunc(targetPIdx, targetIndexMap)) {
                    //verb filter (project a predicate iff both source and target have VERB pos tags),
                    // prevents verb -> non-verb projection
                    if (sourceSent.isVerb(sourcePIdx, sourceIndexMap) && targetSent.isVerb(targetPIdx, targetIndexMap)) {
                        Predicate projectedPredicate = new Predicate();
                        projectedPredicate.setPredicateIndex(targetPIdx);
                        projectedPredicate.setPredicateAutoLabel(pa.getPredicate().getPredicateGoldLabel());
                        projectedPIndices.put(targetPIdx, pa.getPredicate().getPredicateGoldLabel());

                        ArrayList<Argument> sourceArgs = pa.getArguments();
                        ArrayList<Argument> projectedArgs = new ArrayList<>();

                        for (Argument arg : sourceArgs) {
                            int sourceAIdx = arg.getIndex();
                            String sourceAType = arg.getType();

                            if (alignmentDic.containsKey(sourceAIdx)) {
                                int targetArgIndex = alignmentDic.get(sourceAIdx);

                                if (!targetSent.isPunc(targetArgIndex, targetIndexMap)) {
                                    Argument projectedArg = new Argument(targetArgIndex, sourceAType);
                                    projectedArgs.add(projectedArg);

                                    if (!projectedArgIndices.containsKey(targetArgIndex)) {
                                        TreeMap<Integer, String> argInfo = new TreeMap<>();
                                        argInfo.put(targetPIdx, sourceAType);
                                        projectedArgIndices.put(targetArgIndex, argInfo);
                                    } else {
                                        projectedArgIndices.get(targetArgIndex).put(targetPIdx, sourceAType);
                                    }
                                }
                            }
                        }
                        targetPAs.add(new PA(projectedPredicate, projectedArgs));
                    }
                }
            }
        }
        return new Object[]{projectedPIndices, projectedArgIndices, new PAs(targetPAs)};
    }

    public static void writeProjectedRoles(ArrayList<String> targetWords, HashSet<Integer> undecidedList,
                                           HashMap<Integer, String> projectedPIndices,
                                           TreeMap<Integer, TreeMap<Integer, String>> projectedArgIndices,
                                           BufferedWriter projectedFileWriter) throws IOException {
        ArrayList<Integer> targetPIndices = new ArrayList<>(projectedPIndices.keySet());
        Collections.sort(targetPIndices);
        int wordIndex = 0;
        for (String word : targetWords) {
            wordIndex++;
            projectedFileWriter.write(word);

            if (undecidedList.contains(wordIndex))
                for (int i=0; i< targetPIndices.size()+2; i++)
                    projectedFileWriter.write("\t?");
            else{
                //write projected predicates
                if (targetPIndices.contains(wordIndex))
                    projectedFileWriter.write("\tY\t" + projectedPIndices.get(wordIndex));
                else
                    projectedFileWriter.write("\t_\t_");

                //write projected arguments
                if (projectedArgIndices.containsKey(wordIndex)) {
                    for (int pIndex : targetPIndices) {
                        if (projectedArgIndices.get(wordIndex).containsKey(pIndex))
                            projectedFileWriter.write("\t" + projectedArgIndices.get(wordIndex).get(pIndex));
                        else
                            projectedFileWriter.write("\t_");
                    }
                }else
                {
                    for (int pIndex : targetPIndices)
                        projectedFileWriter.write("\t_");
                }
            }
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

    public static boolean foundUndecidedTargetWordsInProjectionOutputs (Set<Integer> projectedPIndices,
                                                                Set<Integer> projectedArgIndices,
                                                                HashSet<Integer> undecidedTargetIndices){
        HashSet<Integer> p = new HashSet<>(projectedPIndices);
        HashSet<Integer> u = new HashSet<>(undecidedTargetIndices);
        p.addAll(projectedArgIndices);
        u.retainAll(p);
        if (u.size() > 0)
            return true;
        else
            return false;
    }

    public static HashSet<Integer> getTargetIndicesWithoutAlignment (HashSet<Integer> targetIndicesWithAlignment, int sentenceLength){
        HashSet<Integer> targetWordsWithoutAlignment = new HashSet<>();
        for (int i =0; i< sentenceLength; i++)
            if (!targetIndicesWithAlignment.contains(i))
                targetWordsWithoutAlignment.add(i);
        return targetWordsWithoutAlignment;
    }
}