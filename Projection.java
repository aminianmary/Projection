/**
 * Created by Maryam Aminian on 10/17/16.
 */
import SentenceStruct.*;
import Structures.IndexMap;
import Structures.ProjectedInfo;
import util.IO;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Projection {
    public static void main(String args[]) throws Exception {
        //args[0]: source file with semantic roles in the conll09 format (input)
        //args[1]: target file (each sentence in a separate line) (input)
        //args[4]: alignment file (input)
        //args[5]: projected file (output)

        String sourceFile = args[0]; //source file has supervised SRL
        String targetFile = args[1]; //target file has supervised/gold SRL (for comparison purposes)
        String alignmentFile = args[2];
        String projectedTargetFile = args[3];
        String sourceClusterFilePath = args[4];
        String targetClusterFilePath = args[5];
        String projectionFilters = args[6];  // PKF (pos kind filter)
        boolean includeSourceInfo = Boolean.parseBoolean(args[7]);
        boolean projectAM = Boolean.parseBoolean(args[8]);
        boolean depFilter = Boolean.parseBoolean(args[9]);

        String filter ="noFilter";
        if (projectionFilters.contains("ps") || projectionFilters.contains("PS"))
            filter = "pos-soft";
        else if (projectionFilters.contains("pe") || projectionFilters.contains("PE"))
            filter = "pos-exact";


        BufferedWriter projectedFileWriter_full = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+"_full_"+filter), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter_full = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile +"_full_"+filter+ ".ids"), "UTF-8"));

        BufferedWriter projectedFileWriter_04 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+"_0.4_"+ filter), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter_04 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile +"_0.4_"+ filter +".ids"), "UTF-8"));

        BufferedWriter projectedFileWriter_03 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+"_0.3_"+ filter), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter_03 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile +"_0.3_"+ filter +".ids"), "UTF-8"));

        BufferedWriter projectedFileWriter_06 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+"_0.6_"+ filter), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter_06 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile +"_0.6_"+filter+ ".ids"), "UTF-8"));

        BufferedWriter projectedFileWriter_08 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+"_0.8_"+ filter), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter_08 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile +"_0.8_"+filter+ ".ids"), "UTF-8"));

        BufferedWriter projectedFileWriter_09 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile+"_0.9_"+ filter), "UTF-8"));
        BufferedWriter projectedSentencesIDWriter_09 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(projectedTargetFile +"_0.9_"+filter+ ".ids"), "UTF-8"));

        Alignment alignment = new Alignment(alignmentFile);
        HashMap<Integer, HashMap<Integer, Integer>> alignmentDic = alignment.getSourceTargetAlignmentDic();

        final IndexMap sourceIndexMap = new IndexMap(sourceFile, sourceClusterFilePath);
        final IndexMap targetIndexMap = new IndexMap(targetFile, targetClusterFilePath);

        ArrayList<String> sourceSents = IO.readCoNLLFile(sourceFile);
        ArrayList<String> targetSents = IO.readCoNLLFile(targetFile);
        int numOfProjectedSentence_full = 0;
        int numOfProjectedSentence_08 = 0;
        int numOfProjectedSentence_09 = 0;
        int numOfProjectedSentence_06 = 0;
        int numOfProjectedSentence_04 = 0;
        int numOfProjectedSentence_03 = 0;

        int projectedSentencesSizeSum_full = 0;
        int projectedSentencesSizeSum_08 = 0;
        int projectedSentencesSizeSum_09 = 0;
        int projectedSentencesSizeSum_06 = 0;
        int projectedSentencesSizeSum_04 = 0;
        int projectedSentencesSizeSum_03 = 0;

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
            HashMap<Integer, Integer> alignDic = alignmentDic.get(senId);

            tAvgSentenceLength += targetSen.getLength();
            Object[] projectionOutput = project(sourceSen, targetSen, alignDic,
                    sourceIndexMap, targetIndexMap, projectionFilters, depFilter, projectAM);

            HashMap<Integer, String> projectedPreds = (HashMap<Integer, String>) projectionOutput[0];
            TreeMap<Integer, TreeMap<Integer, String>> projectedArgs = (TreeMap<Integer, TreeMap<Integer, String>>) projectionOutput[1];
            HashMap<Integer, ProjectedInfo> projectedInfoMap = (HashMap<Integer, ProjectedInfo>) projectionOutput[2];
            numOfTrainingInstances = (Integer) projectionOutput[3];

            //double trainGainPerWord = (double) numOfTrainingInstances / targetSen.getLength();
            double alignedRatio = (double) alignDic.size()/sourceSen.getLength();
            String projectedSen =  writeProjectedRoles(getSentenceForOutput(targetSents.get(senId)),
                    projectedPreds, projectedArgs, projectedInfoMap, includeSourceInfo);

            //writing entire projected corpus
            numOfProjectedSentence_full++;
            projectedSentencesSizeSum_full += targetSen.getLength();
            projectedSentencesIDWriter_full.write(senId + "\n");
            projectedFileWriter_full.write(projectedSen);


            //writing projected sentences with ratio > 0.9
            if (alignedRatio >= 0.9) {
                numOfProjectedSentence_09++;
                projectedSentencesSizeSum_09 += targetSen.getLength();
                projectedSentencesIDWriter_09.write(senId + "\n");
                projectedFileWriter_09.write(projectedSen);
            }


            //writing projected sentences with ratio > 0.8
            if (alignedRatio >= 0.8) {
                numOfProjectedSentence_08++;
                projectedSentencesSizeSum_08 += targetSen.getLength();
                projectedSentencesIDWriter_08.write(senId + "\n");
                projectedFileWriter_08.write(projectedSen);
            }

            //writing projected sentences with ratio > 0.6
            if (alignedRatio >= 0.6) {
                numOfProjectedSentence_06++;
                projectedSentencesSizeSum_06 += targetSen.getLength();
                projectedSentencesIDWriter_06.write(senId + "\n");
                projectedFileWriter_06.write(projectedSen);
            }

            //writing projected sentences with ratio > 0.4
            if (alignedRatio >= 0.4) {
                numOfProjectedSentence_04++;
                projectedSentencesSizeSum_04 += targetSen.getLength();
                projectedSentencesIDWriter_04.write(senId + "\n");
                projectedFileWriter_04.write(projectedSen);
            }

            //writing projected sentences with ratio > 0.3
            if (alignedRatio >= 0.3) {
                numOfProjectedSentence_03++;
                projectedSentencesSizeSum_03 += targetSen.getLength();
                projectedSentencesIDWriter_03.write(senId + "\n");
                projectedFileWriter_03.write(projectedSen);
            }

        }
        System.out.print(sourceSents.size() + "\n");
        System.out.println("Average length of target sentences: " + (double) tAvgSentenceLength / targetSents.size());
        System.out.println("Average length of projected sentences in full corpus: " +
                (double) projectedSentencesSizeSum_full / numOfProjectedSentence_full);

        System.out.println("Average length of projected sentences with train gain > 0.9: " +
                (double) projectedSentencesSizeSum_09 / numOfProjectedSentence_09);

        System.out.println("Average length of projected sentences with train gain > 0.8: " +
                (double) projectedSentencesSizeSum_08 / numOfProjectedSentence_08);

        System.out.println("Average length of projected sentences with train gain > 0.6: " +
                (double) projectedSentencesSizeSum_06 / numOfProjectedSentence_06);

        System.out.println("Average length of projected sentences with train gain > 0.4: " +
                (double) projectedSentencesSizeSum_04 / numOfProjectedSentence_04);

        System.out.println("Average length of projected sentences with train gain > 0.3: " +
                (double) projectedSentencesSizeSum_03 / numOfProjectedSentence_03);


        projectedFileWriter_full.flush();
        projectedFileWriter_full.close();
        projectedSentencesIDWriter_full.flush();
        projectedSentencesIDWriter_full.close();

        projectedFileWriter_08.flush();
        projectedFileWriter_08.close();
        projectedSentencesIDWriter_08.flush();
        projectedSentencesIDWriter_08.close();

        projectedFileWriter_09.flush();
        projectedFileWriter_09.close();
        projectedSentencesIDWriter_09.flush();
        projectedSentencesIDWriter_09.close();

        projectedFileWriter_06.flush();
        projectedFileWriter_06.close();
        projectedSentencesIDWriter_06.flush();
        projectedSentencesIDWriter_06.close();

        projectedFileWriter_04.flush();
        projectedFileWriter_04.close();
        projectedSentencesIDWriter_04.flush();
        projectedSentencesIDWriter_04.close();


        projectedFileWriter_03.flush();
        projectedFileWriter_03.close();
        projectedSentencesIDWriter_03.flush();
        projectedSentencesIDWriter_03.close();

    }

    public static Object[] project(Sentence sourceSent, Sentence targetSent, HashMap<Integer, Integer> alignmentDic,
                                   IndexMap sourceIndexMap, IndexMap targetIndexMap, String projectionFilters,
                                   boolean depFilter, boolean projectAM) throws Exception {
        HashMap<Integer, simplePA> sourceSimplePAMap = sourceSent.getSimplePAMap();
        int[] sourcePosTags = sourceSent.getPosTags();
        int[] sourceDepLabels = sourceSent.getDepLabels();
        int[] sourceDepHeads = sourceSent.getDepHeads();
        int[] targetPosTags = targetSent.getPosTags();
        int[] targetDepLabels = targetSent.getDepLabels();
        int numOfTrainingInstances =0;

        HashMap<Integer, String> projectedPreds = new HashMap<>();
        TreeMap<Integer, TreeMap<Integer, String>> projectedArgs = new TreeMap<>();
        HashMap<Integer, ProjectedInfo> projectedInfoMap = new HashMap<>();

        for (int sourcePIdx : sourceSimplePAMap.keySet()) {
            simplePA sspa = sourceSimplePAMap.get(sourcePIdx);

            if (alignmentDic.containsKey(sourcePIdx)) {
                int targetPIdx = alignmentDic.get(sourcePIdx);
                String p1 = sourceIndexMap.int2str(sourcePosTags[sourcePIdx]);
                String p2 = targetIndexMap.int2str(targetPosTags[targetPIdx]);
                String d1 = sourceIndexMap.int2str(sourceDepLabels[sourcePIdx]);
                String d2 = targetIndexMap.int2str(targetDepLabels[targetPIdx]);
                if (posFilter(projectionFilters, p1, p2) && depFilter(depFilter, d1, d2)) {
                    //analysSourceTargetDependencyMatch predicate label
                    String sourceLabel = sspa.getPredicateLabel();
                    String sourcePOS = sourceIndexMap.int2str(sourcePosTags[sourcePIdx]);
                    String sourcePPOS = sourceIndexMap.int2str(sourcePosTags[sourceDepHeads[sourcePIdx]]);
                    String sourceDepLabel = sourceIndexMap.int2str(sourceDepLabels[sourcePIdx]);

                    projectedInfoMap.put(targetPIdx, new ProjectedInfo(sourcePOS, sourcePPOS, sourceDepLabel));
                    projectedPreds.put(targetPIdx, sourceLabel);
                    HashMap<Integer, String> sourceArgs = sspa.getArgumentLabels();

                    for (int swi = 1; swi < sourceSent.getLength(); swi++) {
                        if (alignmentDic.containsKey(swi)) {
                            int twi = alignmentDic.get(swi);
                            String o1 = sourceIndexMap.int2str(sourcePosTags[swi]);
                            String o2 = targetIndexMap.int2str(targetPosTags[twi]);
                            String k1 = sourceIndexMap.int2str(sourceDepLabels[swi]);
                            String k2 = targetIndexMap.int2str(targetDepLabels[twi]);

                            if (posFilter(projectionFilters, o1, o2) && depFilter(depFilter, k1, k2)) {

                                //analysSourceTargetDependencyMatch word label (either NULL or argument)
                                String twl = "_";
                                if (sourceArgs.containsKey(swi)) {
                                    numOfTrainingInstances++;
                                    String argType = sourceArgs.get(swi);
                                    if (isCoreArgument(argType)) {
                                        twl = argType;
                                    } else {
                                        if (projectAM)
                                            twl = argType;
                                    }
                                }
                                else
                                    numOfTrainingInstances++;

                                String sourcePOS2 = sourceIndexMap.int2str(sourcePosTags[swi]);
                                String sourcePPOS2 = sourceIndexMap.int2str(sourcePosTags[sourceDepHeads[swi]]);
                                String sourceDepLabel2 = sourceIndexMap.int2str(sourceDepLabels[swi]);

                                if (!projectedArgs.containsKey(twi)) {
                                    TreeMap<Integer, String> temp = new TreeMap<>();
                                    temp.put(targetPIdx, twl);
                                    projectedArgs.put(twi, temp);
                                } else if (!projectedArgs.get(twi).containsKey(targetPIdx))
                                    projectedArgs.get(twi).put(targetPIdx, twl);

                                projectedInfoMap.put(twi, new ProjectedInfo(sourcePOS2, sourcePPOS2, sourceDepLabel2));

                            }
                        }
                    }
                }
            }
        }

        //analyseSourceTargetDependencyMatch source non-predicate words
        for (int swi = 1; swi < sourceSent.getLength(); swi++){
            if (!sourceSimplePAMap.containsKey(swi)){
                //not a predicate in the source sentence
                if (alignmentDic.containsKey(swi)) {
                    int twi = alignmentDic.get(swi);
                    String o1 = sourceIndexMap.int2str(sourcePosTags[swi]);
                    String o2 = targetIndexMap.int2str(targetPosTags[twi]);
                    String d1 = sourceIndexMap.int2str(sourceDepLabels[swi]);
                    String d2 = targetIndexMap.int2str(targetDepLabels[twi]);

                    if (posFilter(projectionFilters, o1, o2) && depFilter(depFilter, d1, d2)){
                        //analysSourceTargetDependencyMatch "_" label from source to target
                        String sourcePOS = sourceIndexMap.int2str(sourcePosTags[swi]);
                        String sourcePPOS = sourceIndexMap.int2str(sourcePosTags[sourceDepHeads[swi]]);
                        String sourceDepLabel = sourceIndexMap.int2str(sourceDepLabels[swi]);
                        numOfTrainingInstances++;
                        if (!projectedPreds.containsKey(twi)) {
                            projectedPreds.put(twi, "_");
                            projectedInfoMap.put(twi, new ProjectedInfo(sourcePOS, sourcePPOS, sourceDepLabel));
                        }
                        else
                            System.out.print("ERROR!! Target word is aligned to multiple source words! Sen# " + alignmentDic.toString()+"\n");

                    }
                }
            }

        }

        return new Object[]{projectedPreds, projectedArgs, projectedInfoMap, numOfTrainingInstances};
    }


    public static String writeProjectedRoles(ArrayList<String> targetWords,
                                           HashMap<Integer, String> projectedPreds,
                                           TreeMap<Integer, TreeMap<Integer, String>> projectedArgs,
                                             HashMap<Integer, ProjectedInfo> projectedInfoMap,
                                             boolean addSourceInfo) throws IOException {

        ArrayList<Integer> targetPIndices = new ArrayList<>(projectedPreds.keySet());
        Collections.sort(targetPIndices);
        String output ="";
        int wordIndex = 0;
        for (String word : targetWords) {
            wordIndex++;
            output += word;
            //add projected info from source
            if (addSourceInfo) {
                if (projectedInfoMap.containsKey(wordIndex))
                    output += "\t" + projectedInfoMap.get(wordIndex).getPos() + "\t" +
                            projectedInfoMap.get(wordIndex).getPpos() + "\t" + projectedInfoMap.get(wordIndex).getDepLabel();
                else
                    output += "\tNULL\tNULL\tNULL";
            }

            //write projected predicates
            if (targetPIndices.contains(wordIndex)) {

                if (!projectedPreds.get(wordIndex).equals("_"))
                    output += "\tY\t" + projectedPreds.get(wordIndex);
                else
                    output += "\t_\t" + projectedPreds.get(wordIndex);
            } else {
                output += "\t?\t?";
            }

            //write projected arguments
            if (projectedArgs.containsKey(wordIndex)) {
                for (int pIndex : targetPIndices) {
                    if (!projectedPreds.get(pIndex).equals("_"))
                        if (projectedArgs.get(wordIndex).containsKey(pIndex))
                            output += "\t" + projectedArgs.get(wordIndex).get(pIndex);
                        else
                            output += "\t?";
                }
            } else {
                for (int pIndex : targetPIndices)
                    if (!projectedPreds.get(pIndex).equals("_"))
                        output += "\t?";
            }
            output+= "\n";

        }
        output += "\n";
        return output;
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


    public static boolean samePosSoft(String p1, String p2){

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

    public static boolean samePosExact (String p1, String p2){

        if (p1.equals(p2))
            return true;
        return false;
    }


    public static boolean posFilter(String projectionFilters, String p1, String p2){
        if (projectionFilters.contains("PS") || projectionFilters.contains("ps"))
            return samePosSoft(p1, p2);
        else if (projectionFilters.contains("PE") || projectionFilters.contains("pe"))
            return samePosExact(p1, p2);
        else
            return true;
    }

    public static boolean depFilter (boolean depFilter, String d1, String d2) {
        boolean depMatch = false;
        if (!depFilter)
            depMatch = true;
        else if (d1.equals(d2))
            depMatch = true;
        return depMatch;
    }

    public static boolean isCoreArgument (String str){
        return  Pattern.matches("A[0-9]", str);
    }

}