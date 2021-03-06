import syntaxtree.*;
import visitor.GJDepthFirst;

import java.util.*;

// Stores classes, their propertyTypes, methodTypes, and any subtyping relationships in Context
// HW3: Doesn't do this: and verifies the required acyclic, uniqueness, and no overloading propertyTypes.

// HW3: Emits function table info as Vapor to stdout and stores function table info and member variable info in Context
public class ClassVisitor extends GJDepthFirst<Boolean, Context> {
    private boolean noOverloads(Context context) {
        for (final String s : context.subtypes.keySet()) {
            final HashMap<String, String> methods = context.methodTypes.get(s);
            final HashMap<String, String> methodParameters = context.methodParameters.get(s);
            String parent = s;
            while ((parent = context.subtypes.get(parent)) != null) {
                HashMap<String, String> parentMethods = context.methodTypes.get(parent);
                HashMap<String, String> parentMethodParameters = context.methodParameters.get(parent);
                if (parentMethods == null || parentMethodParameters == null) {
                    return false;
                }
                for (final String method : methods.keySet()) {
                    if (parentMethods.containsKey(method) &&
                            (!parentMethods.get(method).equals(methods.get(method)) ||
                                    !parentMethodParameters.get(method).equals(methodParameters.get(method)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // Let's pretend the runtime complexity of this function is good, so I can sleep at night
    // Also let's pretend this isn't bad and hard to follow code that I wrote in a few hours
    private void calculateOffsetsForClass(Context context, HashSet<String> initializedClasses, HashMap<String, Integer> maxPropertyOffsets, String name) {
        final ArrayList<String> methodOrder = context.methodOrders.get(name);
        final HashMap<String, Integer> methodOffsets = context.methodOffsets.get(name);
        final HashMap<String, Integer> propertyOffsets = context.propertyOffsets.get(name);
        if (!initializedClasses.contains(name)) {
            int maxPropertyOffset = 0;
            if (context.subtypes.containsKey(name)) {
                String parent = context.subtypes.get(name);
                if (!initializedClasses.contains(parent)) {
                    calculateOffsetsForClass(context, initializedClasses, maxPropertyOffsets, parent);
                }
                final HashMap<String, Integer> parentMethodOffsets = context.methodOffsets.get(parent);
                int offsetBase = parentMethodOffsets.size();
                for (final String method : methodOrder) {
                    final Integer offset = parentMethodOffsets.get(method);
                    if (offset == null) {
                        methodOffsets.put(method, offsetBase * 4);
                        offsetBase++;
                    }
                }
                for (final String parentMethod : parentMethodOffsets.keySet()) {
                    methodOffsets.put(parentMethod, parentMethodOffsets.get(parentMethod));
                }
                offsetBase = maxPropertyOffsets.get(parent);
                maxPropertyOffset = offsetBase;
                for (final String property : propertyOffsets.keySet()) {
                    int offset = (propertyOffsets.get(property) * 4) + offsetBase;
                    propertyOffsets.put(property, offset);
                    maxPropertyOffset = maxPropertyOffset > offset ? maxPropertyOffset : offset;
                }
                context.propertyCounts.put(name, propertyOffsets.size() + context.propertyCounts.get(parent));
            } else { // base class
                for (final String method : methodOrder) {
                    methodOffsets.put(method, methodOffsets.get(method) * 4);
                }
                for (final String property : propertyOffsets.keySet()) {
                    final int offset = propertyOffsets.get(property) * 4;
                    propertyOffsets.put(property, offset);
                    maxPropertyOffset = maxPropertyOffset > offset ? maxPropertyOffset : offset;
                }
                context.propertyCounts.put(name, propertyOffsets.size());
            }
            initializedClasses.add(name);
            maxPropertyOffsets.put(name, maxPropertyOffset);
        }
    }

    private void calculateOffsets(Context context) {
        final HashSet<String> initializedClasses = new HashSet<>();
        final HashMap<String, Integer> maxPropertyOffsets = new HashMap<>();
        for (final String name : context.classes) {
            if (!initializedClasses.contains(name)) {
                calculateOffsetsForClass(context, initializedClasses, maxPropertyOffsets, name);
            }
        }
    }

    private void emitFunctionTables(Context context) {
        for (final String name : context.classes) {
            final HashMap<String, Integer> methodOffsets = context.methodOffsets.get(name);
            System.out.println("const vmt_" + name);
            final String prefix = "  :";
            final ArrayList<String> methods = new ArrayList<>();
            methods.addAll(methodOffsets.keySet());
            methods.sort(Comparator.comparing(methodOffsets::get));
            for (final String method : methods) {
                System.out.print(prefix);
                System.out.print(context.lookupMethodDefiningClass(name, method));
                System.out.print(".");
                System.out.println(method);
            }
            System.out.println();
        }
    }

    public Boolean visit(NodeListOptional n, Context argu) {
        if ( n.present() ) {
            Boolean _ret=true;
            for (Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
                _ret = _ret && !Boolean.FALSE.equals(e.nextElement().accept(this,argu));
            }
            return _ret;
        }
        else
            return true;
    }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    @Override
    public Boolean visit(Goal n, Context argu) {
        Boolean _ret = n.f0.accept(this, argu)
                && n.f1.accept(this, argu)
                && noOverloads(argu);
        calculateOffsets(argu);
        n.f2.accept(this, argu);
        emitFunctionTables(argu);
        return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    @Override
    public Boolean visit(MainClass n, Context context) {
        boolean result = context.name(n.f1.f0.tokenImage).push();
        context.pop();
        return result;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    public Boolean visit(TypeDeclaration n, Context argu) {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public Boolean visit(ClassDeclaration n, Context context) {
        boolean result = context.name(n.f1.f0.tokenImage).push();
        result = result
                && n.f3.accept(this, context)
                && n.f4.accept(this, context);
        context.pop();
        return result;
    }

    private boolean insertSubtype(Context context, String c, String d) { //c extends d
        if (context.subtypes.containsKey(c)) {
            return false;
        }
        context.subtypes.put(c, d);
        d = c;
        while ((d = context.subtypes.get(d)) != null) {
            if (d.equals(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public Boolean visit(ClassExtendsDeclaration n, Context context) {
        boolean result = context.name(n.f1.f0.tokenImage).push();
        result = result
                && insertSubtype(context, n.f1.f0.tokenImage, n.f3.f0.tokenImage)
                && n.f5.accept(this, context)
                && n.f6.accept(this, context);
        context.pop();
        return result;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public Boolean visit(VarDeclaration n, Context context) {
        if (context.state == Context.State.Class) {
            HashMap<String, String> fields = context.propertyTypes.get(context.name());
            HashMap<String, Integer> fieldOffsets = context.propertyOffsets.get(context.name());
            fields.put(n.f1.f0.tokenImage, new TypeChecker().visit(n.f0, context));
            fieldOffsets.put(n.f1.f0.tokenImage, fields.size());
            return true;
        } else {
            return true;
        }
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public Boolean visit(MethodDeclaration n, Context context) {
        context.push();
        HashMap<String, String> methods = context.methodTypes.get(context.name());
        ArrayList<String> methodOrder = context.methodOrders.get(context.name());
        HashMap<String, Integer> methodOffsets = context.methodOffsets.get(context.name());
        HashMap<String, String> methodParameters = context.methodParameters.get(context.name());
        methodOffsets.put(n.f2.f0.tokenImage, methods.size());
        TypeChecker tc = new TypeChecker();
        methods.put(n.f2.f0.tokenImage, tc.visit(n.f1, context));
        methodOrder.add(n.f2.f0.tokenImage);
        methodParameters.put(n.f2.f0.tokenImage, tc.visit(n.f4, context));
        context.pop();
        return true;
    }
}
