/**
 * Created by Maryam Aminian on 10/17/16.
 */
import SentenceStruct.*;
import Structures.IndexMap;
import util.IO;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Projection {
    public static void main(String args[]) throws Exception {
        //args[0]: source file with semantic roles in the conll09 format
        //args[1]: target file (each sentence in a separate line)
        //args[4]: alignment file
        //args[5]: projected file

        String sourceFile = args[0]; //source file has supervised SRL
        String targetFile = args[1]; //target file has supervised/gold SRL (for comparing purposes)
        String alignmentFile = args[2];
        String projectedTargetFile = args[3];
        String sourceClusterFilePath = args[4];

        BufferedWriter projectedFileWriter = new BufferedWriter(new FileWriter(projectedTargetFile, true));

        Alignment alignment = new Alignment(alignmentFile);
        HashMap<Integer, HashMap<Integer, Integer>> alignmentDic = alignment.getSourceTargetAlignmentDic();
        final IndexMap sourceIndexMap = new IndexMap(sourceFile, sourceClusterFilePath);

        ArrayList<String> sourceSents = IO.readCoNLLFile(sourceFile);
        ArrayList<String> targetSents = IO.readCoNLLFile(targetFile);

        for (int senId = 0; senId < sourceSents.size(); senId++) {
            if (senId%1000 == 0)
                System.out.print(senId+"...");
            Sentence sourceSen = new Sentence(sourceSents.get(senId), sourceIndexMap);
            Object[] projectionOutput = project(sourceSen, alignmentDic.get(senId));
            HashMap<Integer, String> projectedPIndices = (HashMap<Integer, String>) projectionOutput[0];
            TreeMap<Integer, TreeMap<Integer, String>> projectedArgIndices = (TreeMap<Integer, TreeMap<Integer, String>>) projectionOutput[1];
            writeProjectedRoles(getSentenceForOutput(targetSents.get(senId)) ,projectedPIndices, projectedArgIndices,projectedFileWriter);
        }
        System.out.print(sourceSents.size()+"\n");
        projectedFileWriter.flush();
        projectedFileWriter.close();
    }


    public static Object[] project(Sentence sourceSent, HashMap<Integer, Integer> alignmentDic) throws Exception {
        ArrayList<PA> sourcePAs = sourceSent.getPredicateArguments().getPredicateArgumentsAsArray();
        ArrayList<PA> targetPAs = new ArrayList<>();
        HashMap<Integer, String> projectedPIndices = new HashMap<>();
        TreeMap<Integer, TreeMap<Integer, String>> projectedArgIndices = new TreeMap<>();

        for (PA pa : sourcePAs) {
            int sourcePIdx = pa.getPredicate().getIndex();

            if (alignmentDic.containsKey(sourcePIdx)) {
                int targetPIdx = alignmentDic.get(sourcePIdx);
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
                targetPAs.add(new PA(projectedPredicate, projectedArgs));
            }
        }
        return new Object[]{projectedPIndices, projectedArgIndices, new PAs(targetPAs)};
    }

    public static void writeProjectedRoles(ArrayList<String> targetWords, HashMap<Integer, String> projectedPIndices,
                                           TreeMap<Integer, TreeMap<Integer, String>> projectedArgIndices,
                                           BufferedWriter projectedFileWriter) throws IOException {
        ArrayList<Integer> targetPIndices = new ArrayList<>(projectedPIndices.keySet());
        Collections.sort(targetPIndices);

        int wordIndex = -1;
        for (String word : targetWords) {
            wordIndex++;
            projectedFileWriter.write(word);

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
}