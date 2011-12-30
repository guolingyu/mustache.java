package com.sampullara.mustache.code;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.sampullara.mustache.Code;
import com.sampullara.mustache.DefaultObjectHandler;
import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.MustacheTrace;
import com.sampullara.mustache.ObjectHandler;
import com.sampullara.mustache.Scope;
import com.sampullara.util.FutureWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.TraceClassVisitor;

import static com.sampullara.mustache.Mustache.truncate;

/**
 * Writes a raw value with or without mustache encoding.
 * <p/>
 * User: sam
 * Date: 11/27/11
 * Time: 10:40 AM
 */
public abstract class WriteValueCode implements Code, Opcodes {
  protected final Mustache m;
  protected final String name;
  private final boolean encoded;
  private final int line;
  private static final boolean indy;

  private static class IndyClassLoader extends ClassLoader {
    public Class<?> defineClass(final String name, final byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }

  private static final IndyClassLoader indyCL = new IndyClassLoader();

  static {
    boolean methodHandlePresent = false;
    try {
      Class.forName("java.lang.invoke.MethodHandle");
      // If the class is found, use ASM and indy calls
      methodHandlePresent = true;
      Mustache.logger.info("Using invokedynamic");
    } catch (ClassNotFoundException e) {
      // If the class is not found then use reflection
      Mustache.logger.info("Using reflection");
    }
    indy = methodHandlePresent;
  }

  public static WriteValueCode createWriteValueCode(Mustache m, String name, boolean encoded, int line) {
    if (indy) {
      try {
        String className = getUUID("com.sampullara.mustache.code");
        byte[] bytes = createBytes(className);
        Class<?> indyClass = indyCL.defineClass(className, bytes);
        Constructor<?> constructor = indyClass.getConstructor(Mustache.class,
                String.class, Boolean.TYPE, Integer.TYPE);
        return (WriteValueCode) constructor.newInstance(m, name, encoded, line);
      } catch (InstantiationException e) {
        throw new RuntimeException("Failed to instantiate instance", e);
      } catch (Exception e) {
        throw new RuntimeException("Failed to generate class", e);
      }
    } else return new DefaultWriteValueCode(m, name, encoded, line);
  }

  private static String getUUID(String pkgName) {
    String uuid = UUID.randomUUID().toString().replace("-", "_");
    return pkgName + ".WriteValueClass_" + uuid;
  }

  protected WriteValueCode(Mustache m, String name, boolean encoded, int line) {
    this.m = m;
    this.name = name;
    this.encoded = encoded;
    this.line = line;
  }

  @Override
  public void execute(FutureWriter fw, Scope scope) throws MustacheException {
    MustacheTrace.Event event = null;
    if (Mustache.trace) {
      Object parent = scope.getParent();
      String traceName = parent == null ? scope.getClass().getName() : parent.getClass().getName();
      event = MustacheTrace.addEvent("get: " + name, traceName);
    }
    Object value = getValue(scope);
    if (Mustache.trace) {
      event.end();
    }
    if (value != null) {
      if (value instanceof Future) {
        try {
          fw.enqueue((Future) value);
          return;
        } catch (Exception e) {
          throw new MustacheException("Failed to evaluate future value: " + name, e);
        }
      }
      if (value instanceof FutureWriter) {
        final Object finalValue = value;
        try {
          fw.enqueue(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
              return finalValue;
            }
          });
        } catch (IOException e) {
          throw new MustacheException("Failed to enqueue future writer", e);
        }
      } else {
        String string = String.valueOf(value);
        if (encoded) {
          string = m.encode(string);
        }
        try {
          fw.write(string);
        } catch (IOException e) {
          throw new MustacheException("Failed to write: " + e);
        }
      }
    }
  }

  // We can use this to override with code instead of calls
  protected abstract Object getValue(Scope scope);

  @Override
  public int getLine() {
    return line;
  }

  @Override
  public Scope unexecute(Scope current, String text, AtomicInteger position, Code[] next) throws MustacheException {
    String value = unexecuteValueCode(current, text, position, next);
    if (value != null) {
      BuilderCodeFactory.put(current, name, value);
      return current;
    }
    return null;
  }

  public String unexecuteValueCode(Scope current, String text, AtomicInteger position, Code[] next) throws MustacheException {
    AtomicInteger probePosition = new AtomicInteger(position.get());
    Code[] truncate = truncate(next, 1, null);
    Scope result = null;
    int lastposition = position.get();
    while (next.length > 0 && probePosition.get() < text.length()) {
      lastposition = probePosition.get();
      result = next[0].unexecute(current, text, probePosition, truncate);
      if (result == null) {
        probePosition.incrementAndGet();
      } else {
        break;
      }
    }
    if (result != null) {
      String value = text.substring(position.get(), lastposition);
      if (encoded) {
        value = decode(value);
      }
      position.set(lastposition);
      return value;
    }
    return null;
  }

  @Override
  public void identity(FutureWriter fw) throws MustacheException {
    try {
      if (!encoded) fw.append("{");
      fw.append("{{").append(name).append("}}");
      if (!encoded) fw.append("}");
    } catch (IOException e) {
      throw new MustacheException(e);
    }
  }

  public String decode(String value) {
    return value.replaceAll("&quot;", "\"").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
            .replaceAll("&#10;", "\n").replaceAll("\\\\", "\\").replaceAll("&amp;", "&");
  }

  public static byte[] createBytes(String className) throws Exception {
    className = className.replace(".", "/");

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    MethodVisitor mv;

    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, className,
            null, "com/sampullara/mustache/code/WriteValueCode", null);

    cw.visitSource(className + ".java", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>",
              "(Lcom/sampullara/mustache/Mustache;Ljava/lang/String;ZI)V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitLineNumber(8, l0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitVarInsn(ILOAD, 4);
      mv.visitMethodInsn(INVOKESPECIAL, "com/sampullara/mustache/code/WriteValueCode", "<init>",
              "(Lcom/sampullara/mustache/Mustache;Ljava/lang/String;ZI)V");
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitLineNumber(9, l1);
      mv.visitInsn(RETURN);
      Label l2 = new Label();
      mv.visitLabel(l2);
      mv.visitLocalVariable("this", "L" + className + ";", null, l0, l2, 0);
      mv.visitLocalVariable("m", "Lcom/sampullara/mustache/Mustache;", null, l0, l2, 1);
      mv.visitLocalVariable("name", "Ljava/lang/String;", null, l0, l2, 2);
      mv.visitLocalVariable("encoded", "Z", null, l0, l2, 3);
      mv.visitLocalVariable("line", "I", null, l0, l2, 4);
      mv.visitMaxs(5, 5);
      mv.visitEnd();
    }
    {
      GeneratorAdapter ga = new GeneratorAdapter(ACC_PROTECTED,
              Method.getMethod("Object getValue(com.sampullara.mustache.Scope)"), null, null, cw);
      ga.loadThis();
      ga.getField(Type.getType(className), "name", Type.getType(String.class));
      ga.loadArg(0);
      ga.invokeDynamic("getValue",
              "(Ljava/lang/String;Lcom/sampullara/mustache/Scope;)Ljava/lang/Object;",
              BOOTSTRAP_METHOD);
      ga.returnValue();
      ga.endMethod();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  public static ClassWriter createBridgeClass(String className) throws Exception {
    className = className.replace(".", "/");

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    MethodVisitor mv;

    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null);

    cw.visitSource(className + ".java", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }

    return cw;
  }

  public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
    MethodHandle lookupHandle = MethodHandles.lookup().findStatic(WriteValueCode.class, "lookup",
            MethodType.methodType(Object.class, MutableCallSite.class, String.class, Scope.class));
    MutableCallSite callSite = new MutableCallSite(
            MethodType.methodType(Object.class, String.class, Scope.class));
    callSite.setTarget(lookupHandle.bindTo(callSite));
    return callSite;
  }

  public static Object returnNull() {
    return null;
  }

  public static Object lookup(MutableCallSite callSite, String name, Scope scope) throws Throwable {
    // Here we do the lookup all the way down to the method
    // and generate code to find the the object at runtime in the scope
    Scope originalScope = scope;

    // Find the target field or method
    AccessibleObject ao = null;
    Object parent = originalScope.getParent();
    if (parent != null) {
      ObjectHandler objectHandler = scope.getObjectHandler();
      ao = objectHandler.getMember(name, name.getClass());
    }
    if (ao == null) {
      while (ao == null && (scope = scope.getParentScope()) != null) {
        parent = scope.getParent();
        if (parent != null) {
          ObjectHandler objectHandler = scope.getObjectHandler();
          ao = objectHandler.getMember(name, parent.getClass());
        }
      }
    }
    if (ao == null || ao == DefaultObjectHandler.NOTHING) {
      MethodHandle returnNull = MethodHandles.constant(Object.class, null);
      MethodHandle newTarget = MethodHandles.dropArguments(returnNull, 0, String.class, Scope.class);
      callSite.setTarget(newTarget);
      return null;
    }

    // Second pass to generate the class to access the field / method
    String pkgName = parent.getClass().getPackage().getName();
    String className = getUUID(pkgName);
    ClassWriter classWriter = createBridgeClass(className);
    GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC + ACC_STATIC,
            Method.getMethod("Object getObject(com.sampullara.mustache.Scope)"), null, null,
            classWriter);
    ao = null;
    scope = originalScope;
    parent = scope.getParent();
    if (parent != null) {
      ObjectHandler objectHandler = scope.getObjectHandler();
      ao = objectHandler.getMember(name, name.getClass());
    }
    if (ao == null) {
      ga.loadArg(0);
      while (ao == null && (scope = scope.getParentScope()) != null) {
        parent = scope.getParent();
        if (parent != null) {
          ObjectHandler objectHandler = scope.getObjectHandler();
          ao = objectHandler.getMember(name, parent.getClass());
          if (ao != null) {
            ga.invokeVirtual(Type.getType(Scope.class), Method.getMethod("Object getParent()"));
          }
        } else {
          ga.invokeVirtual(Type.getType(Scope.class), Method.getMethod("Scope getParentScope()"));
        }
      }
    } else {
      ga.loadArg(0);
      ga.invokeVirtual(Type.getType(Object.class), Method.getMethod("Object getParent()"));
    }
    if (ao == null) {
      throw new AssertionError();
    }

    // We have the object on the stack, now we need to call the method or field
    Type parentType = Type.getType(parent.getClass());
    ga.checkCast(parentType);
    if (ao instanceof Field) {
      Field field = (Field) ao;
      ga.getField(parentType, field.getName(), Type.getType(((Field) ao).getType()));
    } else {
      java.lang.reflect.Method method = (java.lang.reflect.Method) ao;
      if (method.getParameterTypes().length == 0) {
        ga.invokeVirtual(parentType, Method.getMethod(method));
      } else {
        ga.loadArg(0);
        ga.invokeVirtual(parentType, Method.getMethod(method));
      }
    }
    ga.returnValue();
    ga.endMethod();
    classWriter.visitEnd();
    byte[] b = classWriter.toByteArray();
    PrintWriter printWriter = new PrintWriter(System.out, true);
    ClassVisitor cv = new TraceClassVisitor(printWriter);
    new ClassReader(b).accept(cv, 0);
    printWriter.flush();
    Class<?> bridgeClass = indyCL.defineClass(className, b);

    MethodHandle targetMethod = MethodHandles.lookup().findStatic(bridgeClass,
            "getObject", MethodType.methodType(Object.class, Scope.class));
    Object o = targetMethod.invokeWithArguments(originalScope);
    targetMethod = MethodHandles.dropArguments(targetMethod, 0, String.class);
    callSite.setTarget(targetMethod);
    return o;
  }

  private static final Handle BOOTSTRAP_METHOD =
          new Handle(H_INVOKESTATIC, "com/sampullara/mustache/code/WriteValueCode", "bootstrap",
                  MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                          MethodType.class).toMethodDescriptorString());
}
