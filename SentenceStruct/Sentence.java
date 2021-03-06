package SentenceStruct;

import Structures.IndexMap;

import java.lang.Object;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

/**
 * Created by Maryam Aminian on 12/9/15.
 */
public class Sentence {

    private int[] depHeads;
    private int[] depLabels;
    private int[] words;
    private int[] wordFullClusterIds;
    private int[] posTags;
    private int[] cPosTags;
    private int[] word4ClusterIds;
    private int[] lemmas;
    private int[] lemmaClusterIds;
    private String[] lemmas_str;
    private TreeSet<Integer>[] reverseDepHeads;
    private PAs predicateArguments;
    private int numOfDirectComponents;
    private int numOfLabeledDirectComponents;
    private String[] fillPredicate;
    HashMap<Integer, HashSet<Integer>> undecidedArgs;

    public Sentence(String sentence, IndexMap indexMap) throws Exception {
        String[] tokens = sentence.trim().split("\n");

        int numTokens = tokens.length + 1; //add one more token for ROOT
        int predicatesSeq = -1;

        depHeads = new int[numTokens];
        depHeads[0] = IndexMap.nullIdx;
        depLabels = new int[numTokens];
        depLabels[0] = IndexMap.nullIdx;
        words = new int[numTokens];
        words[0] = indexMap.str2int("ROOT");
        posTags = new int[numTokens];
        posTags[0] = words[0];
        cPosTags = new int[numTokens];
        cPosTags[0] = words[0];
        lemmas = new int[numTokens];
        lemmas[0] = words[0];
        lemmas_str = new String[numTokens];
        lemmas_str[0] = "ROOT";
        wordFullClusterIds = new int[numTokens];
        wordFullClusterIds[0] = IndexMap.ROOTClusterIdx;
        word4ClusterIds = new int[numTokens];
        word4ClusterIds[0] = IndexMap.ROOTClusterIdx;
        lemmaClusterIds = new int[numTokens];
        lemmaClusterIds[0] = IndexMap.ROOTClusterIdx;
        fillPredicate = new String[numTokens];
        fillPredicate[0] = "_";
        undecidedArgs = new HashMap<>();


        reverseDepHeads = new TreeSet[numTokens];
        predicateArguments = new PAs();

        for (int tokenIdx = 0; tokenIdx < tokens.length; tokenIdx++) {
            String token = tokens[tokenIdx];
            String[] fields = token.split("\t");

            int index = Integer.parseInt(fields[0]);
            int depHead = (fields[9].equals("_")) ? IndexMap.nullIdx : Integer.parseInt(fields[9]);
            depHeads[index] = depHead;

            words[index] = indexMap.str2int(fields[1]);
            wordFullClusterIds[index] = indexMap.getFullClusterId(fields[1]);
            word4ClusterIds[index] = indexMap.get4ClusterId(fields[1]);
            depLabels[index] = indexMap.str2int(fields[11]);
            posTags[index] = indexMap.str2int(fields[5]);
            cPosTags[index] = indexMap.str2int(util.StringUtils.getCoarsePOS(fields[5]));
            lemmas[index] = indexMap.str2int(fields[3]);
            lemmas_str[index] = fields[3];
            lemmaClusterIds[index] = indexMap.getFullClusterId(fields[3]);
            fillPredicate[index] = fields[12];

            if (reverseDepHeads[depHead] == null) {
                TreeSet<Integer> children = new TreeSet<Integer>();
                children.add(index);
                reverseDepHeads[depHead] = children;
            } else
                reverseDepHeads[depHead].add(index);


            String predicateGoldLabel = null;
            if (fields[12].equals("Y")) {
                predicatesSeq++;
                predicateGoldLabel = fields[13];
                predicateArguments.setPredicate(predicatesSeq, index, predicateGoldLabel);
            }

            if (fields.length > 14) //we have at least one argument
            {
                for (int i = 14; i < fields.length; i++) {
                    int associatedPredicateSeq = i - 14;
                    String argumentType = fields[i];
                    if (!argumentType.equals("_")) //found an argument
                    {
                        if (!argumentType.equals("?")) {
                            predicateArguments.setArgument(associatedPredicateSeq, index, argumentType);
                        }else {
                            if (!undecidedArgs.containsKey(associatedPredicateSeq)) {
                                HashSet<Integer> temp = new HashSet<>();
                                temp.add(index);
                                undecidedArgs.put(associatedPredicateSeq, temp);
                            } else {
                                undecidedArgs.get(associatedPredicateSeq).add(index);
                            }
                        }
                    }
                }
            }
        }

        //finding number of annotated direct components
        //note: labeled components are words for them projection has not returned "?"
        for (int i=0; i< numTokens; i++){
            if (isDirectComponent(i, indexMap)) {
                numOfDirectComponents++;
                if (!fillPredicate[i].equals("?"))
                    numOfLabeledDirectComponents++;
            }
        }
    }

    public ArrayList<Integer> getDepPath(int predIndex, int argIndex) {
        Object[] pathInfo = getPath(predIndex, argIndex);
        ArrayList<Integer> pathDepLabels = (ArrayList<Integer>) pathInfo[1];
        ArrayList<Integer> pathDirections = (ArrayList<Integer>) pathInfo[3];
        ArrayList<Integer> depPath= new ArrayList<>();

        for (int i=0; i< pathDepLabels.size(); i++)
            if (pathDepLabels.get(i) != -1)
                depPath.add(pathDepLabels.get(i) << 1 | pathDirections.get(i));
        return depPath;
    }

    public ArrayList<Integer> getPOSPath(int predIndex, int argIndex){
        Object[] pathInfo = getPath(predIndex, argIndex);
        ArrayList<Integer> pathPOSTags = (ArrayList<Integer>) pathInfo[2];
        ArrayList<Integer> pathDirections = (ArrayList<Integer>) pathInfo[3];
        int commonIndex = findCommonIndex((ArrayList<Integer>) pathInfo[1]);
        assert commonIndex!= -1;
        ArrayList<Integer> posPath= new ArrayList<>();

        for (int i=0; i< commonIndex; i++)
            posPath.add(pathPOSTags.get(i) << 1 | pathDirections.get(i));
        for (int j= commonIndex ; j< pathPOSTags.size()-1 ; j++)
            posPath.add(pathPOSTags.get(j) << 1 | pathDirections.get(j+1));
        posPath.add(pathPOSTags.get(pathPOSTags.size()-1));

        return posPath;
    }

    public Object[] getPath(int predIndex, int argIndex){
        int up =0;
        int down =1;
        ArrayList<Integer> predPath=pathToRoot(predIndex);
        ArrayList<Integer> argPath=pathToRoot(argIndex);

        ArrayList<Integer> finalPathWordIndices=new ArrayList<>();
        ArrayList<Integer> finalPathDepLabels=new ArrayList<>();
        ArrayList<Integer> finalPathPOS=new ArrayList<>();
        ArrayList<Integer> finalPathDirections=new ArrayList<>();


        int commonIndex=0;
        int min=(predPath.size()<argPath.size()?predPath.size():argPath.size());
        for(int i=0;i<min;++i) {
            if(predPath.get(i)==argPath.get(i)){ //Always true at root (ie first index)
                commonIndex=i;
            }
        }
        for(int j=predPath.size()-1;j>=commonIndex;--j){
            int wordIdx= predPath.get(j);
            finalPathWordIndices.add(wordIdx);
            if (j==commonIndex)
                finalPathDepLabels.add(-1);
            else
                finalPathDepLabels.add(depLabels[wordIdx]);
            finalPathPOS.add(posTags[wordIdx]);
            finalPathDirections.add(down);
        }
        for(int j=commonIndex+1;j<argPath.size();++j){
            int wordIdx= argPath.get(j);
            finalPathWordIndices.add(wordIdx);
            finalPathDepLabels.add(depLabels[wordIdx]);
            finalPathPOS.add(posTags[wordIdx]);
            finalPathDirections.add(up);
        }
        return new Object[]{finalPathWordIndices, finalPathDepLabels, finalPathPOS, finalPathDirections};
    }

    public  ArrayList<Integer> pathToRoot(int wordIndex){
        ArrayList<Integer> path;
        if(wordIndex == 0){
            //Root element
            path=new ArrayList<Integer>();
            path.add(wordIndex);
            return path;
        }
        path=pathToRoot(depHeads[wordIndex]);
        path.add(wordIndex);
        return path;
    }

    public Integer findCommonIndex (ArrayList<Integer> pathDepLabels)
    {
        for (int i=0; i< pathDepLabels.size(); i++)
            if (pathDepLabels.get(i) ==-1)
                return i;
        return -1;
    }

    public PAs getPredicateArguments() {
        return predicateArguments;
    }

    public int[] getPosTags() {
        return posTags;
    }

    public int[] getcPosTags() {
        return cPosTags;
    }

    public int[] getDepHeads() {
        return depHeads;
    }

    public String[] getDepHeads_as_str() {
        String[] depHeads_str = new String[depHeads.length];
        for (int i = 0; i < depHeads.length; i++)
            depHeads_str[i] = Integer.toString(depHeads[i]);
        return depHeads_str;
    }

    public int[] getWords() {
        return words;
    }

    public int[] getDepLabels() {
        return depLabels;
    }

    public int[] getLemmas() {
        return lemmas;
    }

    public int[] getWordFullClusterIds() {
        return wordFullClusterIds;
    }

    public int[] getWord4ClusterIds() {
        return word4ClusterIds;
    }

    public int[] getLemmaClusterIds() {
        return lemmaClusterIds;
    }

    public String[] getLemmas_str() {
        return lemmas_str;
    }

    public TreeSet<Integer>[] getReverseDepHeads() {
        return reverseDepHeads;
    }

    public HashMap<Integer, String> getPredicatesAutoLabelMap() {
        HashMap<Integer, String> predicatesInfo = new HashMap<Integer, String>();
        for (PA pa : predicateArguments.getPredicateArgumentsAsArray())
            predicatesInfo.put(pa.getPredicate().getIndex(), pa.getPredicate().getPredicateAutoLabel());

        return predicatesInfo;
    }

    public HashMap<Integer, String> getPredicatesGoldLabelMap() {
        HashMap<Integer, String> predicatesInfo = new HashMap<Integer, String>();
        for (PA pa : predicateArguments.getPredicateArgumentsAsArray())
            predicatesInfo.put(pa.getPredicate().getIndex(), pa.getPredicate().getPredicateGoldLabel());

        return predicatesInfo;
    }

    public ArrayList<Predicate> getPredicates () {
        ArrayList<Predicate> predicates = new ArrayList<>();
        for (PA pa: predicateArguments.getPredicateArgumentsAsArray())
            predicates.add(pa.getPredicate());
        return predicates;
    }

    public ArrayList<Integer> getPredicatesIndices() {
        ArrayList<Integer> predicateIndices = new ArrayList<>();
        for (PA pa: predicateArguments.getPredicateArgumentsAsArray())
            predicateIndices.add(pa.getPredicate().getIndex());
        return predicateIndices;
    }

    public void setPDAutoLabels (HashMap<Integer, String> pdAutoLabels){
        assert pdAutoLabels.size() == predicateArguments.getPredicateArgumentsAsArray().size();
        for (PA pa: predicateArguments.getPredicateArgumentsAsArray()){
            int pIdx = pa.getPredicate().getIndex();
            assert pdAutoLabels.containsKey(pIdx);
            pa.getPredicate().setPredicateAutoLabel(pdAutoLabels.get(pIdx));
        }
    }

    public int getLength (){
        return words.length;
    }

    public boolean isPunc (int wordIndex, IndexMap indexMap) throws Exception{
        if (indexMap.int2str(posTags[wordIndex]).equals("."))
            return true;
        return false;
    }

    public boolean isVerb (int wordIndex, IndexMap indexMap) throws Exception{
        if (indexMap.int2str(posTags[wordIndex]).equals("VERB"))
            return true;
        return false;
    }

    public boolean isRoot (int wordIndex){
        if (wordIndex == 0)
            return true;
        else
            return false;
    }

    /**
     * return the first ancestor of the word which is an immediate dependent of a verb
     * @param wordIndex
     * @param indexMap
     * @return
     * @throws Exception
     */
    public int getSyntacticHead (int wordIndex, IndexMap indexMap) throws Exception{
        if (isVerb(depHeads[wordIndex], indexMap) || isRoot(depHeads[wordIndex])) {
            return wordIndex;
        }else
            return getSyntacticHead(depHeads[wordIndex], indexMap);
    }

    public HashMap<Integer, simplePA> getSimplePAMap (){
        HashMap<Integer, simplePA> simplePAMap = new HashMap<>();
        for (PA pa : predicateArguments.getPredicateArgumentsAsArray()){
            int pIdx = pa.getPredicate().getIndex();
            String pLabel = pa.getPredicate().getPredicateGoldLabel();
            HashMap<Integer, String> aMap = new HashMap<>();
            for (Argument a : pa.getArguments())
                aMap.put(a.getIndex(), a.getType());
            simplePA s = new simplePA(pLabel, aMap);
            simplePAMap.put(pIdx, s);
        }
        return simplePAMap;
    }

    public boolean isDirectComponent (int wordIdx, IndexMap indexMap) throws Exception{
        if (indexMap.int2str(posTags[wordIdx]).equalsIgnoreCase("VERB") ||
                indexMap.int2str(posTags[depHeads[wordIdx]]).equalsIgnoreCase("VERB"))
            return true;
        return false;
    }

    public double getCompletenessDegree() {
        if (numOfDirectComponents == 0)
            return 1;
        else
            return (double) numOfLabeledDirectComponents/numOfDirectComponents;
    }

    public HashSet<Integer> getArgIndices(){
        HashSet<Integer> argIndices = new HashSet<>();
        for (PA pa: predicateArguments.getPredicateArgumentsAsArray()){
            argIndices.addAll(pa.getArgumentsIndices());
        }
        return argIndices;
    }

    public HashMap<Integer, HashSet<Integer>> getUndecidedArgs() {
        return undecidedArgs;
    }

    public String[] getFillPredicate() {
        return fillPredicate;
    }
}
