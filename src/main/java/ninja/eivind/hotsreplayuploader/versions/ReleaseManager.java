package ninja.eivind.hotsreplayuploader.versions;

import com.fasterxml.jackson.databind.ObjectMapper;
import ninja.eivind.hotsreplayuploader.models.ReplayFile;
import ninja.eivind.hotsreplayuploader.utils.FileUtils;
import ninja.eivind.hotsreplayuploader.utils.SimpleHttpClient;
import ninja.eivind.hotsreplayuploader.utils.StormHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ReleaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(ReleaseManager.class);
    protected static final String CURRENT_VERSION = "2.0-SNAPSHOT";
    protected static final String GITHUB_MAINTAINER = "eivindveg";
    protected static final String GITHUB_REPOSITORY = "HotSUploader";
    protected static final String GITHUB_RELEASES_ALL
            = "https://api.github.com/repos/{maintainer}/{repository}/releases";
    protected static final String GITHUB_FORMAT_VERSION
            = "http://github.com/{maintainer}/{repository}/releases/tag/{version}";
    @Inject
    private ObjectMapper objectMapper;
    private final GitHubRelease currentRelease;

    private StormHandler stormHandler;
    @Inject
    private SimpleHttpClient httpClient;

    @Inject
    public ReleaseManager(StormHandler stormHandler) {
        this.stormHandler = stormHandler;
        currentRelease = buildCurrentRelease();

        verifyLocalVersion();
    }

    private GitHubRelease buildCurrentRelease() {
        String htmlUrl = getCurrentReleaseString();
        return new GitHubRelease(CURRENT_VERSION, htmlUrl, false);
    }

    public Optional<GitHubRelease> getNewerVersionIfAny() {
        ReleaseComparator releaseComparator = new ReleaseComparator();
        try {
            List<GitHubRelease> latest = getLatest();
            latest.sort(releaseComparator);

            GitHubRelease latestRelease = latest.get(0);
            int compare = releaseComparator.compare(currentRelease, latestRelease);
            if (!latest.isEmpty() && compare > 0) {
                LOG.info("Newer  release is: " + latestRelease);
                return Optional.of(latestRelease);
            } else {
                LOG.info(currentRelease + " is the newest version.");
            }
        } catch (IOException e) {
            LOG.error("Unable to get latest versions", e);
        }
        return Optional.empty();


    }

    private List<GitHubRelease> getLatest() throws IOException {
        ArrayList<GitHubRelease> releases = new ArrayList<>();

        String apiUrl = getAllReleasesString();
        String response = httpClient.simpleRequest(apiUrl);
        releases.addAll(Arrays.asList(objectMapper.readValue(response, GitHubRelease[].class)));
        return releases;
    }

    protected String getAllReleasesString() {
        return GITHUB_RELEASES_ALL.replace("{maintainer}", GITHUB_MAINTAINER)
                .replace("{repository}", GITHUB_REPOSITORY);
    }

    protected String getCurrentReleaseString() {
        return GITHUB_FORMAT_VERSION.replace("{maintainer}", GITHUB_MAINTAINER)
                .replace("{repository}", GITHUB_REPOSITORY)
                .replace("{version}", CURRENT_VERSION);
    }

    private void verifyLocalVersion() {
        try {
            File file = new File(stormHandler.getApplicationHome(), "model_version");
            Long modelVersion;
            if (file.exists()) {
                LOG.info("Reading model version");
                String fileContent = FileUtils.readFileToString(file);
                modelVersion = Long.valueOf(fileContent);

                if (modelVersion < ReplayFile.getSerialVersionUID()) {
                    // TODO IMPLEMENT MIGRATION

                    FileUtils.writeStringToFile(file, String.valueOf(modelVersion));
                }
            } else {
                // Assume first run
                LOG.info("First run: assigning model version");
                FileUtils.writeStringToFile(file, String.valueOf(ReplayFile.getSerialVersionUID()));
            }
        } catch (IOException e) {
            // Expect this to be first run?
            LOG.error("Could not read model version.", e);
        }
    }

    public String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public void setHttpClient(SimpleHttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
