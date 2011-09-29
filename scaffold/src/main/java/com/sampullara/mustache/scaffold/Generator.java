package com.sampullara.mustache.scaffold;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheBuilder;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.Scope;

/**
 * Generate scaffolding for various languages
 */
public class Generator {

  @Argument(alias = "l", description = "Language templates to use")
  private static String language = "java";

  @Argument(alias = "p", description = "Package or namespace")
  private static String pkg = "";

  @Argument(alias = "d", description = "Directory to store generated files")
  private static File directory = new File(".");

  @Argument(alias = "td", description = "Template directory", required = true)
  private static File templateDirectory;

  @Argument(alias = "t", description = "Template to parse", required = true)
  private static File template;

  public static void main(String[] args) throws MustacheException, IOException {
    try {
      Args.parse(Generator.class, args);
    } catch (IllegalArgumentException e) {
      Args.usage(Generator.class);
      System.exit(1);
    }

    MustacheBuilder mb = new MustacheBuilder(templateDirectory);
    JavaCodeFactory jcf = new JavaCodeFactory();
    mb.setCodeFactory(jcf);
    Mustache mustache = mb.parseFile(template.getPath());
    Scope scope = new Scope();
    mustache.execute(new StringWriter(), scope);
    System.out.println(scope);
  }

}
