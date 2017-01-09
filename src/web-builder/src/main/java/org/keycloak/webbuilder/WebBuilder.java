package org.keycloak.webbuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class WebBuilder {

    private static final DateFormat dateIn = new SimpleDateFormat("yyyy-MM-dd");
    private static final DateFormat dateOut = new SimpleDateFormat("dd MMM");

    private final Configuration cfg;
    private final File webDir;
    private final File pagesDir;
    private final File resourcesDir;
    private final File versionsDir;
    private final File newsDir;
    private final File targetDir;
    private Map<String, Object> map;
    private List<Version> versions;
    private List<News> news;

    public static void main(String[] args) throws Exception {
        WebBuilder builder = new WebBuilder(new File("src/web"));
        builder.copyAssets();
        System.out.println("");
        builder.createPages();
        System.out.println("");
    }

    public WebBuilder(File webDir) throws Exception {
        this.webDir = webDir;

        pagesDir = new File(webDir, "pages");
        resourcesDir = new File(webDir, "resources");
        versionsDir = new File(webDir, "versions");
        newsDir = new File(webDir, "news");
        targetDir = new File("").getAbsoluteFile();

        cfg = new Configuration(Configuration.VERSION_2_3_24);
        cfg.setDirectoryForTemplateLoading(webDir);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);

        loadConfig();
    }

    private void loadConfig() throws Exception {
        map = new HashMap<>();

        ObjectMapper mapper = new ObjectMapper();
        Config config = mapper.readValue(new File(webDir, "config.json"), Config.class);
        map.put("config", config);

        File[] versionFiles = versionsDir.listFiles((dir, name) -> {
            return name.endsWith(".json");
        });
        versions = new LinkedList<>();
        for (File versionFile : versionFiles) {
            versions.add(mapper.readValue(versionFile, Version.class));
        }
        Collections.sort(versions);
        map.put("versions", versions);

        versions.get(0).setLatest(true);

        map.put("version", versions.get(0));

        File[] newsFiles = newsDir.listFiles((dir, name) -> {
            return name.endsWith(".json");
        });
        Arrays.sort(newsFiles, (o1, o2) -> o2.getName().compareTo(o1.getName()));
        news = new LinkedList<>();
        for (int i = 0; i < newsFiles.length && i < config.getMaxNews(); i++) {
            News news = mapper.readValue(newsFiles[i], News.class);

            Date date = dateIn.parse(newsFiles[i].getName());
            news.setDate(dateOut.format(date));

            this.news.add(news);
        }
        map.put("news", news);

        System.out.println("Target directory: " + targetDir.getAbsolutePath());
        System.out.println("");
    }

    public void copyAssets() throws Exception {
        System.out.println("Copying resources");
        FileUtils.copyDirectory(resourcesDir, new File(targetDir, "resources"));
    }

    public void createPages() throws Exception {
        System.out.println("Creating pages");
        System.out.println("");

        File[] pageFiles = pagesDir.listFiles((dir, name) -> {
            return name.endsWith(".ftl");
        });

        for (File pageFile : pageFiles) {
            writeFile(map, "pages/" + pageFile.getName(), pageFile.getName().replace(".ftl", ".html"));
        }

        for (Version version : versions) {
            HashMap<String, Object> versionMap = new HashMap<>(map);
            versionMap.put("version", version);

            writeFile(versionMap, "templates/downloads-archive-version.ftl", "downloads-" + version.getVersionShort() + ".html");
            writeFile(versionMap, "templates/documentation-archive-version.ftl", "documentation-" + version.getVersionShort() + ".html");
        }
    }

    private void writeFile(Map<String, Object> map, String template, String output) throws Exception {
        Template downloadTemplate = cfg.getTemplate(template);

        Writer out = new FileWriter(new File(targetDir, output));
        downloadTemplate.process(map, out);

        System.out.println("\t- created: " + output);
    }

}
