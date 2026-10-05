package net.dublinux.arete.engine;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parts of a Maven {@code settings.xml} that decide where a {@code maven:} policy source is
 * fetched from and as whom: the local repository, mirrors, servers (credentials), proxies, and the
 * repositories of active profiles. CI sets these in {@code settings.xml}, so the engine reads them
 * rather than asking for the same facts twice.
 *
 * <p>Read as Maven reads them, within limits: {@code ${env.NAME}} and system-property placeholders are
 * interpolated; profiles are active when listed in {@code activeProfiles}, active by default, or
 * activated by a system property; mirrors match by {@code mirrorOf} ({@code *}, {@code external:*},
 * ids, {@code !id}). Encrypted passwords ({@code {...}}) are refused with a clear message: put the
 * secret in an environment variable and refer to it as {@code ${env.NAME}}.
 */
final class MavenSettings {
    record Mirror(String id, String mirrorOf, String url) { }
    record Server(String id, String username, String password, Map<String, String> headers) { }
    record Proxy2(String id, boolean active, String protocol, String host, int port, String username, String password, String nonProxyHosts) { }
    record Profile(String id, boolean activeByDefault, String propertyName, String propertyValue, Map<String, String> repositories) { }
    /** A repository to try: its base URL (already mirrored) and the headers to send. */
    record Repository(String url, Map<String, String> headers) { }

    static final String CENTRAL = "https://repo.maven.apache.org/maven2";

    private Path localRepository;
    private final List<Mirror> mirrors = new ArrayList<>();
    private final Map<String, Server> servers = new LinkedHashMap<>();
    private final List<Proxy2> proxies = new ArrayList<>();
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Set<String> activeProfiles = new LinkedHashSet<>();

    private MavenSettings() { }

    /**
     * Reads the usual settings: the global file under {@code $MAVEN_HOME/conf}, then the user's
     * {@code ~/.m2/settings.xml} over it. Either may be absent.
     */
    static MavenSettings load() {
        MavenSettings settings = new MavenSettings();
        String home = firstNonBlank(System.getenv("MAVEN_HOME"), System.getenv("M2_HOME"), System.getProperty("maven.home"));
        if (home != null) settings.merge(Path.of(home, "conf", "settings.xml"));
        settings.merge(Path.of(System.getProperty("user.home", ""), ".m2", "settings.xml"));
        return settings;
    }

    /** Reads several files in order, a later one over an earlier (global then user, as Maven does); a missing one is skipped. */
    static MavenSettings load(List<Path> files) {
        MavenSettings settings = new MavenSettings();
        for (Path file : files) if (file != null) settings.merge(file);
        return settings;
    }

    /** Reads one file, as {@code mvn -s file} does. The file must exist. */
    static MavenSettings load(Path file) {
        MavenSettings settings = new MavenSettings();
        if (!Files.isRegularFile(file)) throw new BundleValidationException("Maven settings file " + file + " does not exist");
        settings.merge(file);
        return settings;
    }

    private void merge(Path file) {
        if (!Files.isRegularFile(file)) return;
        Element root;
        try (InputStream in = Files.newInputStream(file)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(in);
            root = document.getDocumentElement();
        } catch (Exception e) {
            throw new BundleValidationException("Maven settings " + file + " could not be read: " + e.getMessage());
        }
        String local = text(root, "localRepository");
        if (local != null) localRepository = Path.of(interpolate(local));
        for (Element e : children(child(root, "mirrors"), "mirror")) {
            mirrors.add(new Mirror(text(e, "id"), text(e, "mirrorOf"), interpolate(text(e, "url"))));
        }
        for (Element e : children(child(root, "servers"), "server")) {
            Map<String, String> headers = new LinkedHashMap<>();
            Element config = child(e, "configuration");
            for (Element property : children(child(config, "httpHeaders"), "property")) {
                String name = text(property, "name");
                if (name != null) headers.put(name, interpolate(text(property, "value")));
            }
            servers.put(text(e, "id"), new Server(text(e, "id"), interpolate(text(e, "username")), interpolate(text(e, "password")), headers));
        }
        for (Element e : children(child(root, "proxies"), "proxy")) {
            String port = text(e, "port");
            proxies.add(new Proxy2(text(e, "id"), !"false".equalsIgnoreCase(text(e, "active")), text(e, "protocol"), interpolate(text(e, "host")),
                    port == null ? 80 : Integer.parseInt(port.strip()), interpolate(text(e, "username")), interpolate(text(e, "password")),
                    interpolate(text(e, "nonProxyHosts"))));
        }
        for (Element e : children(child(root, "profiles"), "profile")) {
            Element activation = child(e, "activation");
            Element property = child(activation, "property");
            Map<String, String> repositories = new LinkedHashMap<>();
            for (Element repository : children(child(e, "repositories"), "repository")) {
                if (!"false".equalsIgnoreCase(text(child(repository, "releases"), "enabled"))) {
                    repositories.put(text(repository, "id"), interpolate(text(repository, "url")));
                }
            }
            profiles.put(text(e, "id"), new Profile(text(e, "id"), "true".equalsIgnoreCase(text(activation, "activeByDefault")),
                    text(property, "name"), text(property, "value"), repositories));
        }
        for (Element e : children(child(root, "activeProfiles"), "activeProfile")) activeProfiles.add(e.getTextContent().strip());
    }

    Path localRepository() {
        return localRepository != null ? localRepository : Path.of(System.getProperty("user.home", ""), ".m2", "repository");
    }

    /**
     * The repositories to try for a Maven coordinate, in order: those of the active profiles, then Maven
     * Central; each replaced by its mirror when one applies, de-duplicated, with the credentials of the
     * matching {@code <server>}.
     */
    List<Repository> repositories(Set<String> extraProfiles) {
        Map<String, String> declared = new LinkedHashMap<>();
        for (Profile profile : profiles.values()) {
            if (isActive(profile, extraProfiles)) declared.putAll(profile.repositories());
        }
        declared.putIfAbsent("central", CENTRAL);

        Map<String, Repository> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> repo : declared.entrySet()) {
            String url = repo.getValue();
            String authId = repo.getKey();
            Mirror mirror = mirrorFor(repo.getKey(), url);
            if (mirror != null) {
                url = mirror.url();
                authId = mirror.id();
            }
            result.putIfAbsent(url, new Repository(url, headersFor(authId)));
        }
        return List.copyOf(result.values());
    }

    private boolean isActive(Profile profile, Set<String> extraProfiles) {
        if (activeProfiles.contains(profile.id()) || extraProfiles.contains(profile.id()) || profile.activeByDefault()) return true;
        String name = profile.propertyName();
        if (name == null) return false;
        boolean negated = name.startsWith("!");
        String actual = System.getProperty(negated ? name.substring(1) : name);
        boolean matches = profile.propertyValue() == null ? actual != null : profile.propertyValue().equals(actual);
        return negated ? !matches : matches;
    }

    private Mirror mirrorFor(String repositoryId, String url) {
        for (Mirror mirror : mirrors) {
            if (matches(mirror.mirrorOf(), repositoryId, url)) return mirror;
        }
        return null;
    }

    /** Maven's {@code mirrorOf}: a comma list of ids, {@code *}, {@code external:*}, and {@code !id} exclusions. */
    static boolean matches(String mirrorOf, String repositoryId, String url) {
        if (mirrorOf == null) return false;
        boolean any = false;
        for (String token : mirrorOf.split(",")) {
            String t = token.strip();
            if (t.isEmpty()) continue;
            if (t.startsWith("!")) {
                if (t.substring(1).equals(repositoryId)) return false;
                continue;
            }
            if (t.equals(repositoryId) || t.equals("*")) any = true;
            else if (t.equals("external:*") && isExternal(url)) any = true;
        }
        return any;
    }

    private static boolean isExternal(String url) {
        if (url.startsWith("file:")) return false;
        String host = URI.create(url).getHost();
        return host != null && !host.equals("localhost") && !host.equals("127.0.0.1");
    }

    private Map<String, String> headersFor(String serverId) {
        Server server = servers.get(serverId);
        if (server == null) return Map.of();
        Map<String, String> headers = new LinkedHashMap<>(server.headers());
        if (server.username() != null && server.password() != null && !headers.containsKey("Authorization")) {
            String password = clear(serverId, server.password());
            headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString((server.username() + ":" + password).getBytes(StandardCharsets.UTF_8)));
        }
        for (Map.Entry<String, String> header : headers.entrySet()) clear(serverId, header.getValue());
        return headers;
    }

    private static String clear(String id, String value) {
        if (value.startsWith("{") && value.endsWith("}") && !value.contains("${")) {
            throw new BundleValidationException("settings.xml server '" + id + "' has an encrypted password, which is not supported; "
                    + "put the secret in an environment variable and use ${env.NAME}");
        }
        if (value.contains("${")) {
            throw new BundleValidationException("settings.xml server '" + id + "' uses " + value.substring(value.indexOf("${"))
                    + ", which is not set");
        }
        return value;
    }

    /** A proxy selector from the first active proxy, honouring {@code nonProxyHosts}; null when there is none. */
    ProxySelector proxySelector() {
        for (Proxy2 proxy : proxies) {
            if (!proxy.active() || proxy.host() == null) continue;
            Proxy target = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.host(), proxy.port()));
            List<Pattern> direct = new ArrayList<>();
            if (proxy.nonProxyHosts() != null) {
                for (String pattern : proxy.nonProxyHosts().split("[|,]")) {
                    if (!pattern.isBlank()) direct.add(Pattern.compile(Pattern.quote(pattern.strip()).replace("*", "\\E.*\\Q")));
                }
            }
            return new ProxySelector() {
                @Override public List<Proxy> select(URI uri) {
                    String host = uri.getHost();
                    if (host == null) return List.of(Proxy.NO_PROXY);
                    for (Pattern pattern : direct) if (pattern.matcher(host).matches()) return List.of(Proxy.NO_PROXY);
                    return List.of(target);
                }
                @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
            };
        }
        return null;
    }

    /** Credentials for the active proxy, or null. */
    Authenticator proxyAuthenticator() {
        for (Proxy2 proxy : proxies) {
            if (proxy.active() && proxy.host() != null && proxy.username() != null) {
                String password = proxy.password() == null ? "" : clear("proxy " + proxy.id(), proxy.password());
                return new Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        return getRequestorType() == RequestorType.PROXY
                                ? new PasswordAuthentication(proxy.username(), password.toCharArray()) : null;
                    }
                };
            }
        }
        return null;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /** {@code ${env.NAME}} and system properties; anything else is left as written. */
    static String interpolate(String value) {
        if (value == null) return null;
        return interpolate(value, name -> name.startsWith("env.") ? System.getenv(name.substring(4)) : System.getProperty(name));
    }

    static String interpolate(String value, Function<String, String> lookup) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String resolved = lookup.apply(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved != null ? resolved : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element e && e.getTagName().equals(name)) return e;
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> list = new ArrayList<>();
        if (parent == null) return list;
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && e.getTagName().equals(name)) list.add(e);
        }
        return list;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().strip();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
