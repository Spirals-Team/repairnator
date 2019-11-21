package fr.inria.spirals.repairnator.realtime.utils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single-line-change filter.
 * */
public class PatchFilter {
    
    static final String HUNK_HEADER_REGEX = "^@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@[ \\w{\\(\\)]*$";
    static final String SPLIT_BY_HUNKS_REGEX = "(?<=\\n)(?=@@ -\\d+,\\d+ \\+\\d+,\\d+ @@[ \\w{\\(\\)]*\\n)";
    
    enum State {
        ENTRY,
        HEADER,
        ENTRY_CONTEXT,
        EXIT_CONTEXT,
        REMOVE,
        ADD
    }
    
    public class HunkLines {
        public HunkLines(String removed, String added) {
            this.removed = removed;
            this.added = added;
        }
        
        public final String removed;
        public final String added;
    }
    
    String[] splitByLines(String hunk) {
        return hunk.split("\n");
    }
    
    boolean isRegularHunkHeader(String hunkHeader) {

        Pattern p = Pattern.compile(HUNK_HEADER_REGEX);
        Matcher m = p.matcher(hunkHeader);
        
        if(m.find()) {
            if(m.group(1).equals(m.group(3)) && m.group(2).equals(m.group(4))) {
                return true;
            }
        }
        return false;
    }
    
    int getHunkLine(String hunkHeader) {

        Pattern p = Pattern.compile(HUNK_HEADER_REGEX);
        Matcher m = p.matcher(hunkHeader);
        
        if(m.find()) {
            return Integer.parseInt(m.group(3));   
        }
        return -1;
        
    }
    
    boolean test(String[] hunkLines) {

        State state = State.ENTRY;
        
        for(String line : hunkLines) {
            switch(state) {
            
            //assuming first line is hunk header.
            case ENTRY:{
                state = State.HEADER;
                break;
            }
            
            case HEADER:{
                char first = line.charAt(0);
                if(first == '-') {
                    state = State.REMOVE;
                } else if(first == ' ') {
                    state = State.ENTRY_CONTEXT;
                } else if(first == '+') {
                    return false;
                }
                
            }break;
            
            case ENTRY_CONTEXT:{
                char first = line.charAt(0);
                if(first == '-') {
                    state = State.REMOVE;
                } else if(first == ' ') {
                    state = State.ENTRY_CONTEXT;
                } else {
                    return false;
                }
            }break;
            
            case REMOVE:{
                char first = line.charAt(0);
                if(first == '+') {
                    state = State.ADD;
                }else{
                    return false;
                }
            }break;
            
            case ADD:{
                char first = line.charAt(0);
                if(first == ' ') {
                    state = State.EXIT_CONTEXT;
                }else{
                    return false;
                }
            }break;
            
            case EXIT_CONTEXT:{
                char first = line.charAt(0);
                if(first == ' ') {
                    state = State.EXIT_CONTEXT;
                }else{
                    return false;
                }
            }break;
            
            }
        }
        
        
        return state == State.EXIT_CONTEXT || state == State.ADD;
    }
    
    public ArrayList<String> getHunks(ArrayList<String> patches, boolean singleHunk, int hunkDistance){
        
        ArrayList<String> ret = new ArrayList<String>();

        for(String patch : patches) {
            String[] hunks = patch.split(SPLIT_BY_HUNKS_REGEX);
            
            ArrayList<Boolean> oneLineHunks  = new ArrayList<Boolean>();
            ArrayList<Integer> linePositions  = new ArrayList<Integer>();
            
            if(singleHunk && hunks.length > 1) continue;
            
            for(String hunk: hunks) {
                String[] lines = splitByLines(hunk);
                
                linePositions.add(getHunkLine(lines[0]));
                oneLineHunks.add(isRegularHunkHeader(lines[0]) && test(lines));
            } 
            
            for(int i = 0; i < oneLineHunks.size() - 1; ++i) {
                boolean isFarEnough = oneLineHunks.get(i) && ( linePositions.get(i + 1) - linePositions.get(i) > hunkDistance);
                oneLineHunks.set(i, isFarEnough);
            }
            
            for(int i = oneLineHunks.size() - 1; i > 0; --i) {
                boolean isFarEnough = oneLineHunks.get(i) && ( linePositions.get(i) - linePositions.get(i - 1) > hunkDistance);
                oneLineHunks.set(i, isFarEnough);
            }
            
            for(int i = 0; i < oneLineHunks.size(); ++i) {
                if(oneLineHunks.get(i)){
                    ret.add(hunks[i]);
                }
            }
            
        }
        return ret;
    }
    
        
    
    public HunkLines parse(String hunk) {
        String[] lines = splitByLines(hunk);
        
        String removed= "";
        String added = "";
        
        for(String line : lines) {
            char first = line.charAt(0);
            if(first == '-') removed = line.substring(1).trim();
            if(first == '+') added = line.substring(1).trim();
        }
        
        return new HunkLines(removed, added);
    }
}
