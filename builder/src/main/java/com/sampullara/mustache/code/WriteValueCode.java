package com.sampullara.mustache.code;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.sampullara.mustache.Code;
import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.MustacheTrace;
import com.sampullara.mustache.Scope;
import com.sampullara.util.FutureWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

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
      String uuid = UUID.randomUUID().toString().replace("-", "_");
      try {
        String className = "com.sampullara.mustache.code.WriteValueClass_" + uuid;
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

    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, className,
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
      GeneratorAdapter ga = new GeneratorAdapter(ACC_PROTECTED, Method.getMethod("Object getValue(com.sampullara.mustache.Scope)"), null, null, cw);
      ga.loadThis();
      ga.getField(Type.getType(className), "m", Type.getType(Mustache.class));
      ga.loadArg(0);
      ga.loadThis();
      ga.getField(Type.getType(className), "name", Type.getType(String.class));
      ga.invokeVirtual(Type.getType(Mustache.class),
              Method.getMethod("Object getValue(com.sampullara.mustache.Scope, String)"));
      ga.returnValue();
      ga.endMethod();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

}
