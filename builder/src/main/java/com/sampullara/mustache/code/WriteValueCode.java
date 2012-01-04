package com.sampullara.mustache.code;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.sampullara.mustache.Code;
import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.MustacheTrace;
import com.sampullara.mustache.Scope;
import com.sampullara.mustache.indy.IndyUtil;
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

  public static WriteValueCode createWriteValueCode(Mustache m, String name, boolean encoded, int line) {
    if (Mustache.indy) {
      try {
        String simpleClassName = "WriteValueCode";
        String pkgName = "com.sampullara.mustache.code";
        String superClassName = pkgName + "." + simpleClassName;
        String className = IndyUtil.getUUID(pkgName, simpleClassName);
        return (WriteValueCode) IndyUtil.defineClass(className, createClass(className, superClassName))
                .getConstructor(Mustache.class, String.class, Boolean.TYPE, Integer.TYPE)
                .newInstance(m, name, encoded, line);
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

  public static byte[] createClass(String className, String superClassName) throws Exception {
    className = className.replace(".", "/");
    superClassName = superClassName.replace(".", "/");

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    MethodVisitor mv;

    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, className,
            null, superClassName, null);

    cw.visitSource(className + ".java", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>",
              "(Lcom/sampullara/mustache/Mustache;Ljava/lang/String;ZI)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitVarInsn(ILOAD, 4);
      mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>",
              "(Lcom/sampullara/mustache/Mustache;Ljava/lang/String;ZI)V");
      mv.visitInsn(RETURN);
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
              IndyUtil.BOOTSTRAP_METHOD);
      ga.returnValue();
      ga.endMethod();
    }
    cw.visitEnd();

    return cw.toByteArray();
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
      if (value.equals("") && next != null && next.length > 0) {
        /*
        This doesn't work either
        Trying to handle {{#include}}{{name}}{{/include}} case and
        {{name}}{{other}} case cleanly when one is empty
        */
        try {
          // Let's see if the next matches iff WriteCode
          if (next[0] instanceof WriteCode) {
            Code[] truncate = truncate(next, 1, null);
            if (next[0].unexecute(current, text, position, truncate) != null) {
              return null;
            }
          }
        } catch (MustacheException me) {
          // Fall through
        }
        // Didn't match next, put in empty string
      }
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
}
