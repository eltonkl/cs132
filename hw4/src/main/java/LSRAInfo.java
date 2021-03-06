import cs132.util.IndentPrinter;
import cs132.vapor.ast.VFunction;
import cs132.vapor.ast.VVarRef;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class LSRAInfo {
    static final boolean debug = false;
    static StringWriter stringWriter = new StringWriter();
    static IndentPrinter indentPrinter = new IndentPrinter(stringWriter, "  ");

    public class LiveIn {
        public int start;
        public int end;
        public boolean readFrom = false;

        public LiveIn(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public class LinearRange {
        public String variable;
        public int start;
        public int end;
        public boolean crossCall;

        public LinearRange(String variable, int start, int end) {
            this.variable = variable;
            this.start = start;
            this.end = end;
        }
    }

    public class FunctionInfo {
        public int in;
        public int out;
        public int local;
        public HashMap<String, LinearRange> linearRanges;
    }

    public HashMap<String, FunctionInfo> functionsInfo = new HashMap<>();

    public String currentFunction;
    public FunctionInfo currentFunctionInfo;
    public HashMap<String, LinearRange> currentLinearRanges = new HashMap<>();
    public HashMap<String, ArrayList<LiveIn>> currentLiveIns = new HashMap<>();
    public HashSet<String> currentVariables = new HashSet<>();
    public ArrayList<LinearRange> currentCrossCalls = new ArrayList<>();
    public int currentLine;

    public void enterFunction(VFunction vFunction) {
        this.currentFunction = vFunction.ident;
        this.currentFunctionInfo = new FunctionInfo();
        for (VVarRef.Local local : vFunction.params) {
            startLiveIn(local.ident);
        }
    }

    public void exitFunction() {
        currentFunctionInfo.linearRanges = currentLinearRanges;
        currentLinearRanges = new HashMap<>();
        currentLiveIns = new HashMap<>();
        currentVariables = new HashSet<>();
        currentCrossCalls = new ArrayList<>();
        functionsInfo.put(currentFunction, currentFunctionInfo);
        allocations.clear();
        lifetimes.clear();
        currentParameter = 0;
        stringWriter = new StringWriter();
        indentPrinter = new IndentPrinter(stringWriter, "  ");
    }

    public void setCurrentLine(int currentLine) {
        this.currentLine = currentLine;
    }

    public void startLiveIn(String variable) throws RuntimeException {
        if (!currentLiveIns.containsKey(variable)) {
            currentLiveIns.put(variable, new ArrayList<>());
        }
        currentLinearRanges.putIfAbsent(variable, new LSRAInfo.LinearRange(variable, currentLine, -1));
        ArrayList<LiveIn> liveIns = currentLiveIns.get(variable);
        if (liveIns.size() == 0 || liveIns.get(liveIns.size() - 1).readFrom) {
            liveIns.add(new LiveIn(currentLine + 1, currentLine + 1));
        } else {
            liveIns.set(liveIns.size() - 1, new LiveIn(currentLine + 1, currentLine + 1));
        }
    }

    public void extendLiveIn(String variable) {
        currentVariables.add(variable);
        ArrayList<LiveIn> liveIns = currentLiveIns.get(variable);
        LiveIn current = liveIns.get(liveIns.size() - 1);
        current.end = currentLine;
        current.readFrom = true;
    }

    public void calculateLinearRanges() {
        for (String variable : currentVariables) {
            ArrayList<LiveIn> liveIns = currentLiveIns.get(variable);
            if (liveIns != null) {
                int end = -1;
                // need to mark reads after writes to calculate these correctly
                for (LiveIn liveIn : liveIns) {
                    if (liveIn.start != -1 && liveIn.end != -1) {
                        end = end > liveIn.end ? end : liveIn.end;
                    }
                }
                if (end != -1) {
                    currentLinearRanges.get(variable).end = end;
                } else {

                }
            }
        }
    }

    public void calculateCalleeSavedVariables() {
        currentCrossCalls.sort(Comparator.comparingInt((c -> c.start)));
        class interval {
            int point;
            boolean start;
            interval(int point, boolean start) {
                this.point = point;
                this.start = start;
            }
        }
        ArrayList<interval> intervals = new ArrayList<>();
        for (LinearRange linearRange : currentCrossCalls) {
            intervals.add(new interval(linearRange.start, true));
            intervals.add(new interval(linearRange.end, false));
        }
        intervals.sort((c1, c2) -> {
            if (c1.point < c2.point) {
                return -1;
            } else if (c1.point > c2.point) {
                return 1;
            } else if (c1.start && !c2.start) {
                return 1;
            } else if (c2.start && !c1.start) {
                return -1;
            } else {
                return 0;
            }
        });
        int max = 0;
        int cur = 0;
        for (interval i : intervals) {
            if (i.start) {
                cur++;
            } else {
                cur--;
            }
            max = max > cur ? max : cur;
        }
        currentFunctionInfo.local = max;
    }

    public HashMap<String, String> allocations = new HashMap<>();
    public HashMap<String, Integer> lifetimes = new HashMap<>();
    public final ArrayList<String> temporaries = new ArrayList<>(Arrays.asList("$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8"));
    public final ArrayList<String> saveds = new ArrayList<>(Arrays.asList("$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7", "$v1"));

    public String getTemporary(int end) {
        for (String temporary : temporaries) {
            Integer temporaryEnd = lifetimes.get(temporary);
            if (temporaryEnd == null || temporaryEnd < currentLine) {
                lifetimes.put(temporary, end);
                return temporary;
            }
        }
        // spill
        return null;
    }

    public String getSaved(int end) {
        for (String saved : saveds) {
            Integer savedEnd = lifetimes.get(saved);
            if (savedEnd == null || savedEnd < currentLine) {
                lifetimes.put(saved, end);
                return saved;
            }
        }
        // spill
        return null;
    }

    public ArrayList<String> parameters = new ArrayList<>(Arrays.asList("$a0", "$a1", "$a2", "$a3"));
    public int currentParameter;
    public String getParameterAssignment(String source) {
        if (currentParameter >= parameters.size()) {
            return "out[" + String.valueOf(currentParameter++ - parameters.size()) + "] = " + source;
        } else {
            return parameters.get(currentParameter++) + " = " + source;
        }
    }
}
