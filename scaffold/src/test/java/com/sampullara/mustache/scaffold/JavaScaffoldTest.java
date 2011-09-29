package com.sampullara.mustache.scaffold;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import javax.annotation.PreDestroy;

import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheBuilder;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.MustacheJava;
import org.junit.BeforeClass;
import org.junit.Test;

public class JavaScaffoldTest {
  @Test
  public void testIt() throws IOException, MustacheException {
    MustacheBuilder mj = init();

    Mustache m = mj.parse(getContents(root, "complex.html"));

  }

  private static File root;

  private MustacheBuilder init() {
    return new MustacheBuilder(root);
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

  @BeforeClass
  public static void setup() {
    File file = new File("src/test/resources");
    root = new File(file, "complex.html").exists() ? file : new File("../src/test/resources");
  }
}
