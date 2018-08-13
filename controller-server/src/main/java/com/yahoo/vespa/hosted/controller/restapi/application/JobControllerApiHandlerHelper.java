package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.RunDetails;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements the REST API for the job controller delegated from the Application API.
 *
 * @see JobController
 * @see ApplicationApiHandler
 */
class JobControllerApiHandlerHelper {

    /**
     * @return Response with all job types that have recorded runs for the application _and_ the status for the last run of that type
     */
    static HttpResponse jobTypeResponse(List<JobType> sortedJobs, Map<JobType, RunStatus> lastStatus, URI baseUriForJobs) {
        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        Cursor jobArray = responseObject.setArray("jobs");

        sortedJobs.forEach(jobType ->
                jobTypeToSlime(jobArray.addObject(), jobType, Optional.ofNullable(lastStatus.get(jobType)), baseUriForJobs));
        return new SlimeJsonResponse(slime);
    }

    private static void jobTypeToSlime(Cursor cursor, JobType jobType, Optional<RunStatus> runStatus, URI baseUriForJobs) {
        Cursor jobObject = cursor.setObject(jobType.jobName());

        // Url that are specific to the jobtype
        String jobTypePath = baseUriForJobs.getPath() + "/" + jobType.jobName();
        URI baseUriForJobType = baseUriForJobs.resolve(jobTypePath);
        jobObject.setString("url", baseUriForJobType.toString());

        // Add the last run status for the jobtype if present
        runStatus.ifPresent(status -> {
            Cursor lastObject = jobObject.setObject("last");
            runStatusToSlime(lastObject, status, baseUriForJobType);
        });
    }

    /**
     * @return Response with the runstatuses for a specific jobtype
     */
    static HttpResponse runStatusResponse(Map<RunId, RunStatus> runStatuses, URI baseUriForJobType) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();

        runStatuses.forEach((runid, runstatus) -> runStatusToSlime(cursor.setObject(Long.toString(runid.number())), runstatus, baseUriForJobType));

        return new SlimeJsonResponse(slime);
    }

    private static void runStatusToSlime(Cursor cursor, RunStatus runStatus, URI baseUriForJobType) {
        runStatus.result().ifPresent(result -> cursor.setString("result", result.name()));
        runStatus.end().ifPresent(instant -> cursor.setString("end", instant.toString()));

        Cursor stepsArray = cursor.setArray("steps");
        runStatus.steps().forEach((step, status) -> {
            Cursor stepObject = stepsArray.addObject();
            stepObject.setString(step.name(), status.name());
        });

        cursor.setString("start", runStatus.start().toString());
        cursor.setLong("id", runStatus.id().number());

        cursor.setString("targetPlatform", runStatus.versions().targetPlatform().toString());
        applicationVersionToSlime(cursor.setObject("targetApplication"), runStatus.versions().targetApplication());
        runStatus.versions().sourcePlatform().ifPresent(version -> cursor.setString("sourcePlatform", version.toString()));
        runStatus.versions().sourceApplication().ifPresent(version -> applicationVersionToSlime(cursor.setObject("sourceApplication"), version));

        String logsPath = baseUriForJobType.getPath() + "/run/" + runStatus.id().number();
        cursor.setString("logs", baseUriForJobType.resolve(logsPath).toString());
    }

    private static void applicationVersionToSlime(Cursor cursor, ApplicationVersion version) {
        cursor.setLong("build", version.buildNumber().get());
        cursor.setString("repository", version.source().get().repository());
        cursor.setString("branch", version.source().get().branch());
        cursor.setString("commit", version.source().get().commit());
    }

    /**
     * @return Response with logs from a single run
     */
    static HttpResponse runDetailsResponse(JobController jobController, RunId runId) {
        Slime slime = new Slime();
        Cursor logsObject = slime.setObject();

        RunDetails runDetails = jobController.details(runId).orElseThrow(() ->
                new NotExistsException(String.format(
                        "No run details exist for application: %s, job type: %s, number: %d",
                        runId.application().toShortString(), runId.type().jobName(), runId.number())));
        for (Step step : Step.values()) {
            runDetails.get(step).ifPresent(stepLog -> logsObject.setString(step.name(), stepLog));
        }

        return new SlimeJsonResponse(slime);
    }

    /**
     * Unpack payload and submit to job controller. Defaults instance to 'default' and renders the
     * application version on success.
     *
     * @return Response with the new application version
     */
    static HttpResponse submitResponse(JobController jobController, String tenant, String application,
                                       SourceRevision sourceRevision, byte[] appPackage, byte[] testPackage) {
        ApplicationVersion version = jobController.submit(ApplicationId.from(tenant, application, "default"),
                sourceRevision, appPackage, testPackage);

        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        responseObject.setString("version", version.id());
        return new SlimeJsonResponse(slime);
    }
}

