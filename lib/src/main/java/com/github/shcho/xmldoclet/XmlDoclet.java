package com.github.shcho.xmldoclet;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Doclet.Option.Kind;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Doclet generating Javadoc as XML with
 * <a href="https://openjdk.org/groups/compiler/using-new-doclet.html">the new Doclet API</a>.
 */
public class XmlDoclet implements Doclet {

    // constants
    private static final boolean OK = true;
    private static final boolean ERROR = false;

    // defaults
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final String DEFAULT_OUTPUT_DIR = ".";
    private static final String DEFAULT_OUTPUT_FILENAME = "javadoc.xml";

    // internal
    private DocTrees docTrees;
    private Reporter reporter;

    // options
    private String outputDir = DEFAULT_OUTPUT_DIR;
    private String filename = DEFAULT_OUTPUT_FILENAME;
    private final Set<Option> options = Set.of(
            // NOTE: this must be "-d" because of the Standard doclet
            new Option("-d", true,
                       "Destination directory for output file. (Default: %s)".formatted(DEFAULT_OUTPUT_DIR),
                       "[<directory>]", Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    String outputDir = arguments.isEmpty() ? DEFAULT_OUTPUT_DIR : arguments.get(0);
                    outputDir = outputDir.replaceAll("/+$", "");  // remove trailing slashes
                    if (!Files.isDirectory(Paths.get(outputDir))) {
                        reporter.print(Diagnostic.Kind.ERROR, "Invalid output directory: " + outputDir);
                        return ERROR;
                    }

                    XmlDoclet.this.outputDir = outputDir;
                    return OK;
                }
            },
            new Option("-filename", true, "Output filename. (Default: %s)".formatted(DEFAULT_OUTPUT_FILENAME),
                       "[<filename>]", Kind.OTHER) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    final String filename = arguments.isEmpty() ? DEFAULT_OUTPUT_FILENAME : arguments.get(0);

                    XmlDoclet.this.filename = filename;
                    return OK;
                }
            });
    /**
     * For now {@code -doctitle} and {@code -windowtitle} option is not needed,
     * but just allow it to make gradle happy.
     * <br>
     * Without this, gradle will fail with the error like below:
     * <pre>
     * error: option -doctitle not allowed
     * </pre>
     */
    private final Set<Option> ignoredOptions = Set.of(
            new Option("-doctitle", true, "IGNORED", "[<doctitle>]",
                       Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    reporter.print(Diagnostic.Kind.NOTE, "Option %s is ignored".formatted(option));
                    return OK;
                }
            },
            new Option("-windowtitle", true, "IGNORED", "[<windowtitle>]",
                       Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    reporter.print(Diagnostic.Kind.NOTE, "Option %s is not ignored".formatted(option));
                    return OK;
                }
            },
            new Option("-notimestamp", false, "IGNORED", null, Kind.STANDARD) {
                @Override
                public boolean process(String option, List<String> arguments) {
                    reporter.print(Diagnostic.Kind.NOTE, "Option %s is not ignored".formatted(option));
                    return OK;
                }
            });

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Stream.concat(options.stream(), ignoredOptions.stream())
                     .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment docEnv) {
        docTrees = docEnv.getDocTrees();

        // print runtime information
        reporter.print(Diagnostic.Kind.NOTE, "Output directory: " + outputDir);
        reporter.print(Diagnostic.Kind.NOTE, "Output filename: " + filename);

        try (final FileWriter fileWriter = new FileWriter(outputDir + '/' + filename)) {
            final XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(fileWriter);
            writer.writeStartDocument(DEFAULT_ENCODING, "1.0");
            writer.writeStartElement("root");

            for (PackageElement packageElement : ElementFilter.packagesIn(docEnv.getIncludedElements())) {
                processPackage(writer, packageElement);
            }

            writer.writeEndElement(); // root
            writer.writeEndDocument();
            writer.close();
        } catch (Exception e) {
            reporter.print(Diagnostic.Kind.ERROR, "Error generating XML: " + e.getMessage());
            return ERROR;
        }

        return OK;
    }

    private void processPackage(final XMLStreamWriter writer, final PackageElement packageElement)
            throws Exception {
        writer.writeStartElement("package");
        writer.writeAttribute("name", packageElement.getQualifiedName().toString());

        for (final TypeElement typeElement : ElementFilter.typesIn(packageElement.getEnclosedElements())) {
            processType(writer, typeElement);
        }

        writer.writeEndElement(); // package
    }

    private void processType(final XMLStreamWriter writer, final TypeElement typeElement) throws Exception {
        final String elementName = typeElement.getKind() == ElementKind.CLASS ? "class" : "interface";
        writer.writeStartElement(elementName);
        writer.writeAttribute("name", typeElement.getSimpleName().toString());
        writer.writeAttribute("qualified", typeElement.getQualifiedName().toString());

        final DocCommentTree docCommentTree = docTrees.getDocCommentTree(typeElement);
        if (docCommentTree != null) {
            writer.writeStartElement("comment");
            writer.writeCharacters(docCommentTree.toString());
            writer.writeEndElement(); // comment
        }

        for (final VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            processField(writer, field);
        }

        writer.writeEndElement(); // class or interface
    }

    private void processField(final XMLStreamWriter writer, final VariableElement field) throws Exception {
        writer.writeStartElement("field");
        writer.writeAttribute("name", field.getSimpleName().toString());

        final DocCommentTree docCommentTree = docTrees.getDocCommentTree(field);
        if (docCommentTree != null) {
            writer.writeStartElement("comment");
            writer.writeCharacters(docCommentTree.toString());
            writer.writeEndElement();
        }

        writer.writeEndElement();
    }

    abstract class Option implements Doclet.Option {

        private final String name;
        private final boolean hasArg;
        private final String description;
        private final String parameters;
        private final Kind kind;

        Option(String name, boolean hasArg, String description, String parameters, Kind kind) {
            this.name = name;
            this.hasArg = hasArg;
            this.description = description;
            this.parameters = parameters;
            this.kind = kind;
        }

        @Override
        public int getArgumentCount() {
            return hasArg ? 1 : 0;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public List<String> getNames() {
            return List.of(name);
        }

        @Override
        public String getParameters() {
            return hasArg ? parameters : "";
        }
    }
}

