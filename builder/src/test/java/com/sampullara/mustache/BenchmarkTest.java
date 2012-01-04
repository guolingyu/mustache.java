package com.sampullara.mustache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import com.sampullara.util.FutureWriter;
import junit.framework.TestCase;

/**
 * Compare compilation with interpreter.
 * <p/>
 * User: sam
 * Date: 5/14/11
 * Time: 9:28 PM
 */
public class BenchmarkTest extends TestCase {
  private static final int TIME = 2000;
  private File root;

  protected void setUp() throws Exception {
    super.setUp();
    File file = new File("src/test/resources");
    root = new File(file, "simple.html").exists() ? file : new File("../src/test/resources");
  }

  protected String getContents(File root, String file) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(root, file)),"UTF-8"));
    StringWriter capture = new StringWriter();
    char[] buffer = new char[8192];
    int read;
    while ((read = br.read(buffer)) != -1) {
      capture.write(buffer, 0, read);
    }
    return capture.toString();
  }

  public void testComplex() throws MustacheException, IOException {
    FutureWriter.setParallel();
    System.out.println("complex.html evaluations per millisecond:");
    FutureWriter.setParallel();
    for (int i = 0; i < 2; i++) {
      {
        MustacheBuilder c = new MustacheBuilder(root);
        Mustache m = c.parseFile("complex.html");
        complextest(m);
        long start = System.currentTimeMillis();
        int total = 0;
        while (true) {
          complextest(m);
          total++;
          if (System.currentTimeMillis() - start > TIME) break;
        }
        System.out.println("Interpreted parallel: " + total/TIME);
      }
      {
        Mustache m = handcoded();
        complextest(m);
        long start = System.currentTimeMillis();
        int total = 0;
        while (true) {
          complextest(m);
          total++;
          if (System.currentTimeMillis() - start > TIME) break;
        }
        System.out.println("Hand coded parallel: " + total/TIME);
      }
    }
    FutureWriter.setParallel(null);
    for (int i = 0; i < 2; i++) {
      {
        MustacheBuilder c = new MustacheBuilder(root);
        Mustache m = c.parseFile("complex.html");
        complextest(m);
        long start = System.currentTimeMillis();
        int total = 0;
        while (true) {
          complextest(m);
          total++;
          if (System.currentTimeMillis() - start > TIME) break;
        }
        System.out.println("Interpreted serial: " + total/TIME);
      }
      {
        Mustache m = handcoded();
        complextest(m);
        long start = System.currentTimeMillis();
        int total = 0;
        while (true) {
          complextest(m);
          total++;
          if (System.currentTimeMillis() - start > TIME) break;
        }
        System.out.println("Hand coded serial: " + total/TIME);
      }
    }
  }

  private Mustache handcoded() {
    final BenchmarkTest.ComplexObject co = new BenchmarkTest.ComplexObject();
    return new Mustache() {
      Mustache item = new Mustache() {
        @Override
        public void execute(FutureWriter writer, Scope ctx) throws MustacheException {
          BenchmarkTest.ComplexObject.Color color = (BenchmarkTest.ComplexObject.Color) ctx.getParent();
          try {
            if (color.current) {
              writer.write("      <li><strong>");
              writer.write(color.name);
              writer.write("</strong></li>\n");
            }
            if (co.link(ctx)) {
              writer.write("      <li><a href=\"");
              writer.write(color.url);
              writer.write(">");
              writer.write(color.name);
              writer.write("</a></li>\n");
            }
          } catch (IOException e) {
            throw new MustacheException(e);
          }
        }
      };
      @Override
      public void execute(FutureWriter writer, Scope ctx) throws MustacheException {
        try {
          writer.write("<h1>");
          writer.write(co.header);
          writer.write("</h1>\n");
          Scope s = new Scope(co);
          if (co.list(s)) {
            writer.write("  <ul>\n");
            for (BenchmarkTest.ComplexObject.Color color : co.item) {
              item.execute(writer, new Scope(color));
            }
            writer.write("  </ul>\n");
          }
          if (co.empty(s)) {
            writer.write("  <p>The list is empty.</p>\n");
          }
          if (!co.empty(s)) {
            writer.write("  <p>The list is not empty.</p>\n");
          }
        } catch (IOException e) {
          throw new MustacheException(e);
        }
      }
    };
  }

  private StringWriter complextest(Mustache m) throws MustacheException, IOException {
    Scope scope = new Scope(new ComplexObject());
    StringWriter sw = new StringWriter();
    FutureWriter writer = new FutureWriter(sw);
    m.execute(writer, scope);
    writer.flush();
    return sw;
  }

  public static class ComplexObject {
    String header = "Colors";
    List<Color> item = Arrays.asList(
            new Color("red", true, "#Red"),
            new Color("green", false, "#Green"),
            new Color("blue", false, "#Blue")
    );

    boolean link(Scope s) {
      return !((Boolean) s.get("current"));
    }

    boolean list(Scope s) {
      return ((List) s.get("item")).size() != 0;
    }

    boolean empty(Scope s) {
      return ((List) s.get("item")).size() == 0;
    }

    public static class Color {
      Color(String name, boolean current, String url) {
        this.name = name;
        this.current = current;
        this.url = url;
      }
      public String name;
      public boolean current;
      public String url;
    }
  }
}
