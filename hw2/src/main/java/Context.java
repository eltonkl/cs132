import java.util.HashMap;
import java.util.HashSet;

public class Context {
    public enum State {
        Root,
        Class,
        Function
    }
    public State state = State.Root;
    private String name;
    // Filled in by ClassVisitor
    public HashSet<String> classes = new HashSet<>();
    public HashMap<String, HashMap<String, String>> properties = new HashMap<>();
    public HashMap<String, HashMap<String, String>> methods = new HashMap<>();
    public HashMap<String, HashMap<String, String>> methodParameters = new HashMap<>();
    public HashMap<String, String> subtypes = new HashMap<>(); // string1 extends string2
    // Used by TypeChecker
    private HashMap<String, String> fields = new HashMap<>();
    private HashMap<String, String> parameters = new HashMap<>();
    private HashMap<String, String> locals = new HashMap<>();
    private boolean type = false;
    private boolean staticFunction = false;

    public Context staticFunction() {
        this.staticFunction = true;
        return this;
    }

    public Context unstaticFunction() {
        this.staticFunction = false;
        return this;
    }

    public boolean isStaticFunction() {
        return staticFunction;
    }

    public Context type() {
        this.type = true;
        return this;
    }

    public Context untype() {
        this.type = false;
        return this;
    }

    public Context name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return this.name;
    }

    public boolean push() {
        if (state == State.Root) {
            state = State.Class;
            if (!addClass(name)) {
                return false;
            }
            if (!properties.containsKey(name)) {
                properties.put(name, new HashMap<>());
            }
        } else if (state == State.Class) {
            state = State.Function;
        } else if (state == State.Function) {
            return false;
        }
        return true;
    }

    public boolean pop() {
        if (state == State.Class) {
            fields.clear();
            state = State.Root;
        } else if (state == State.Function) {
            parameters.clear();
            locals.clear();
            state = State.Class;
        } else if (state == State.Root) {
            return false;
        }
        return true;
    }

    private boolean addClass(String identifier) {
        if (classes.contains(identifier)) {
            return false;
        } else {
            classes.add(identifier);
            return true;
        }
    }

    public boolean addField(String identifier, String type) {
        if (state == State.Class) {
            fields.put(identifier, type);
            return true;
        } else {
            return false;
        }
    }

    public String getField(String identifier) {
        return fields.get(identifier);
    }

    public boolean addParameter(String identifier, String type) {
        if (state == State.Function) {
            parameters.put(identifier, type);
            return true;
        } else {
            return false;
        }
    }

    public String getParameter(String identifier) {
        return parameters.get(identifier);
    }

    public boolean addLocal(String identifier, String type) {
        if (state == State.Function) {
            locals.put(identifier, type);
            return true;
        } else {
            return false;
        }
    }

    public String getLocal(String identifier) {
        return locals.get(identifier);
    }

    public boolean isSubtype(String c, String d) { // c extends d
        if (c.equals(d)) {
            return true;
        } else {
            while ((c = subtypes.get(c)) != null) {
                if (c.equals(d)) {
                    return true;
                }
            }
            return false;
        }
    }

    public String lookupMethod(String name, String method) {
        HashMap<String, String> methods;
        String parent = name;
        do {
            methods = this.methods.get(parent);
            if (methods.containsKey(method)) {
                return methods.get(method);
            }
        } while ((parent = subtypes.get(parent)) != null);

        return null;
    }

    public String lookupMethodParameters(String name, String method) {
        HashMap<String, String> methodParameters;
        String parent = name;
        do {
            methodParameters = this.methodParameters.get(parent);
            if (methodParameters.containsKey(method)) {
                return methodParameters.get(method);
            }
        } while ((parent = subtypes.get(parent)) != null);

        return null;
    }

    public String lookupIdentifier(String identifier) {
        if (type) {
            return identifier;
        } else if (locals.containsKey(identifier)) {
            return locals.get(identifier);
        } else if (parameters.containsKey(identifier)) {
            return parameters.get(identifier);
        } else if (fields.containsKey(identifier)) {
            return fields.get(identifier);
        } else if (state == State.Function) {
            String name = this.name;
            while ((name = subtypes.get(name)) != null) {
                String result = properties.get(name).get(identifier);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}