package Structures;

/**
 * Created by Maryam Aminian on 3/8/17.
 */
public class ProjectedInfo {
    String pos;
    String ppos; //pos of its patent
    String depLabel;

    public ProjectedInfo(String p, String pp, String dl){
        pos = p;
        ppos = pp;
        depLabel = dl;
    }
    public String getPos() {
        return pos;
    }

    public String getPpos() {
        return ppos;
    }

    public String getDepLabel() {
        return depLabel;
    }
}
