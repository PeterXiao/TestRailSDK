package com.rmn.testrail.service;

import com.rmn.testrail.entity.*;
import com.rmn.testrail.entity.Error;
import com.rmn.testrail.parameters.ApiFilterValue;
import com.rmn.testrail.parameters.ApiParameter;
import com.rmn.testrail.parameters.ApiParameters;
import com.rmn.testrail.util.HTTPUtils;
import com.rmn.testrail.util.JSONUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mmerrell
 */
public class TestRailService implements Serializable {
    private Logger log = LoggerFactory.getLogger(getClass());

    /**
     * This might not last forever--we'll need to make "v2" a variable at some point--but this works for the moment
     */
    private static final String ENDPOINT_SUFFIX = "index.php?/api/v2/%s%s";

    /**
     * Used this way, the default implementation will assume that the TestRail instance is hosted by TestRail on their server. As such, you pass in
     * your "client ID", and it will get put into the correct place. If you're hosting a local instance, you'll have to use the (URL, String, String)
     * constructor in order to pass the full URL for your instance
     */
    private String apiEndpoint = "https://%s.testrail.com/";
    private String username;
    private String password;
    private HTTPUtils utils = new HTTPUtils();

    public TestRailService() {}

    /**
     * Construct a new TestRailService with the necessary information to start communication immediately
     * @param clientId The clientID--usually the "<id>.testrail.com" you are assigned when you first open an account
     * @param username The username you will use to communicate with the API. It is recommended to create an account with minimal privileges, specifically for API use
     * @param password The password to use with this account
     */
    public TestRailService(String clientId, String username, String password) {
        this.apiEndpoint = String.format(apiEndpoint, clientId) + ENDPOINT_SUFFIX;
        this.username = username;
        this.password = password;
    }

    /**
     * Construct a new TestRailService against a local instance. This requires you to pass the FULL URL of the local instance, including your client ID
     * @param apiEndpoint The full URL of the service you are using (only the domain, not the "index.php" part. It should look like "https://server-ip/testRail/",
     *                    including the final '/')
     * @param username The username you will use to communicate with the API. It is recommended to create an account with minimal privileges, specifically for API use
     * @param password The password to use with this account
     */
    public TestRailService(URL apiEndpoint, String username, String password) {
        this.apiEndpoint = apiEndpoint.toString();
        this.username = username;
        this.password = password;
    }

    /**
     * Sets the "API Endpoint" for the TestRails service--this if for locally-hosted instances of TestRail, and should
     * include the full base URL, e.g. "https://secure-ip/testrail/", including the final forward-slash "/"
     * @param apiEndpoint Your API end-point (including the Client ID)
     */
    public void setApiEndpoint(URL apiEndpoint) {
        this.apiEndpoint = apiEndpoint.toString() + ENDPOINT_SUFFIX;
    }

    /**
     * Sets the "client id" for the TestRails service--this usually equates to the lowest-level
     * domain, e.g. http://[foo].testrail.com...
     * @param clientId Your Client ID (provided by TestRails)
     */
    public void setClientId(String clientId) { this.apiEndpoint = String.format(apiEndpoint, clientId) + ENDPOINT_SUFFIX; }

    /**
     * The user name for the API-enabled user
     * @param username Your Username
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * The user name for the API-enabled user
     * @param password Your Password
     */
    public void setPassword(String password) { this.password = password; }

    /**
     * Sets the HTTPUtils object (this is not static because we need to stub these methods for unit testing)
     * @param utils The HTTPUtils object
     */
    public void setHttpUtils(HTTPUtils utils) { this.utils = utils; }

    /**
     * Uses generics and magic to return any kind of TestRails Entity in List context (i.e. a list of Entities)
     * @param clazz The Class you're interested in mapping the JSON to--must derive from BaseEntity
     * @param apiCall The TestRails end-point you're going after (just the call itself, e.g. "get_projects".
     *                The v2 and authentication are provided elsewhere)
     * @param param The rest of the URL you're querying. You're on your own as to how to construct this parameter--
     *              consult the API documentation (http://docs.gurock.com/testrail-api/start) for more details
     * @param <T> The Type of BaseEntity you're trying to construct. As long as your 'clazz' param extends BaseEntity,
     *           this will be taken care of
     * @return A List of the Entity you're trying to get from the Service
     */
    protected  <T extends BaseEntity> List<T> getEntityList(Class<T> clazz, String apiCall, String param) {
        HttpURLConnection connection = getRESTRequest(apiCall, param);
        log.debug("");
        String contents = utils.getContentsFromConnection(connection);
        List<T> entities = JSONUtils.getMappedJsonObjectList(clazz, contents);
        for (T suite: entities) {
            suite.setTestRailService(this);
        }
        return entities;
    }

    /**
     * Uses generics and magic to return any kind of TestRails Entity in Single context (i.e. a single Entity, not a List)
     * @param clazz The Class you're interested in mapping the JSON to--must derive from BaseEntity
     * @param apiCall The TestRails end-point you're going after (just the call itself, e.g. "get_project".
     *                The v2 and authentication are provided elsewhere)
     * @param param The rest of the URL you're querying. You're on your own as to how to construct this parameter--
     *              consult the API documentation (http://docs.gurock.com/testrail-api/start) for more details
     * @param <T> The Type of BaseEntity you're trying to construct. As long as your 'clazz' param extends BaseEntity,
     *           this will be taken care of
     * @return The Entity you're trying to get from the Service
     */
    protected  <T extends BaseEntity> T getEntitySingle(Class<T> clazz, String apiCall, String param) {
        HttpURLConnection connection = getRESTRequest(apiCall, param);
        String contents = utils.getContentsFromConnection(connection);
        T entity = JSONUtils.getMappedJsonObject(clazz, contents);
        entity.setTestRailService(this);
        return entity;
    }

    /**
     * Pings the API, mainly to ensure that your credentials are correct
     * @return Whether or not it was able to establish a successful connection
     */
    public boolean verifyCredentials() throws IOException {
        //At the moment this just grabs a list of projects and makes sure the response code is valid. The API does not have
        // a "version" or "ping" end-point, so this seemed like the only way to talk to it without knowing some data first
        HttpURLConnection connection = getRESTRequest(TestRailCommand.GET_PROJECTS.getCommand(), "");
        return connection.getResponseCode() == 200;
    }


    //BEGIN API HELPER METHODS
    //================================================================

    //API: Cases------------------------------------------------------

    /**
     * Returns the TestCase with the given id
     * @param testCaseId The TestCase ID (in TestRails, this will be something like 'C7', but just provide the 7)
     * @return The TestCase associated with this id
     */
    public TestCase getTestCase(int testCaseId) {
        return getEntitySingle(TestCase.class, TestRailCommand.GET_CASE.getCommand(), Integer.toString(testCaseId));
    }

    /**
     * Get the complete list of all test cases in this Project (if the project is operating in single suite mode)
     * @return the List of TestCase entities associated with this TestSuite
     */
    public List<TestCase> getTestCasesSingleSuiteMode(int projectId, ApiFilterValue... apiFilters) {
        return getTestCases(projectId, -1, -1, apiFilters);
    }

    /**
     * Get the complete list of all test cases in this Project (if the project is operating in single suite mode)
     * @param sectionId The Section ID
     * @return the List of TestCase entities associated with this TestSuite
     */
    public List<TestCase> getTestCasesSingleSuiteMode(int projectId, int sectionId, ApiFilterValue... apiFilters) {
        return getTestCases(projectId, -1, sectionId, apiFilters);
    }

    /**
     * Get the complete list of all test cases in this TestSuite
     * @param suiteId The Suite ID (in TestRails, this will be something like 'S7', but just provide the 7)
     * @return the List of TestCase entities associated with this TestSuite
     */
    public List<TestCase> getTestCases(int projectId, int suiteId, ApiFilterValue... apiFilters) {
        return getTestCases(projectId, suiteId, -1, apiFilters);
    }

    /**
     * Get the list of test cases in this TestSuite for the Section indicated
     * @param suiteId The Suite ID (in TestRails, this will be something like 'S7', but just provide the 7)
     * @param sectionId The Section ID
     * @return A List of the TestCases in this Suite
     */
    public List<TestCase> getTestCases(int projectId, int suiteId, int sectionId, ApiFilterValue... apiFilters) {
        String params = Integer.toString(projectId);
        if (suiteId > 0) {
            params += ApiParameters.append(ApiParameter.SUITE_ID, suiteId);
        }
        if (sectionId > 0) {
            params += ApiParameters.append(ApiParameter.SECTION_ID, sectionId);
        }
        for (ApiFilterValue apiFilter : apiFilters) {
            params += apiFilter.append();
        }
        return getEntityList(TestCase.class, TestRailCommand.GET_CASES.getCommand(), params);
    }

    /**
     * Creates a new test case.
     * @param sectionId The ID of the section the test case should be added to
     */
    public TestCase addTestCase(TestCase testCase, int sectionId) {
        return postRESTBodyReturn(TestRailCommand.ADD_CASE.getCommand(), Integer.toString(sectionId), testCase, TestCase.class);
    }

    /**
     * Updates an existing test case (partial updates are supported, i.e. you can submit and update specific fields only).
     * @param testCase a TestCase object with fields to be updated set. (i.e. TestCase updateCase = new TestCase(); updateCase.setPriorityId(2); )
     * @param caseId The ID of the test case
     * @return
     */
    public TestCase updateTestCase(TestCase testCase, int caseId) {
        return postRESTBodyReturn(TestRailCommand.UPDATE_CASE.getCommand(), Integer.toString(caseId), testCase, TestCase.class);
    }

    /**
     * WARNING: Permanently an existing test case. Please note: Deleting a test case cannot be undone and also permanently deletes all test results in active test runs (i.e. test runs that haven't been closed (archived) yet).
     * @param caseId The ID of the test case
     */
    public void deleteTestCase(int caseId) {
        postRESTBody(TestRailCommand.DELETE_CASE.getCommand(), Integer.toString(caseId), null);
    }


    //API: Case Fields------------------------------------------------

    /**
     * Returns a list of available test case custom fields.
     * @return String with JSON response, you must parse the string yourself
     */
    public String getCaseFields() {
        HttpURLConnection connection = getRESTRequest(TestRailCommand.GET_CASE_FIELDS.getCommand(), null);
        return utils.getContentsFromConnection(connection);
    }

    //API: Case Types-------------------------------------------------

    /**
     * Returns a list of available case types.
     * @return String with JSON response, you must parse the string yourself
     */
    public String getCaseTypes() {
        HttpURLConnection connection = getRESTRequest(TestRailCommand.GET_CASE_TYPES.getCommand(), null);
        return utils.getContentsFromConnection(connection);
    }
    //API: Configurations---------------------------------------------

    /**
     * Returns a list of available configurations, grouped by configuration groups (requires TestRail 3.1 or later).
     * @param projectId
     * @return String with JSON response, you must parse the string yourself
     */
    public String getConfigurations(int projectId) {
        HttpURLConnection connection = getRESTRequest(TestRailCommand.GET_CONFIGS.getCommand(), Integer.toString(projectId));
        return utils.getContentsFromConnection(connection);
    }

    /**
     * Creates a new configuration group.
     * @param name The name of the configuration group
     * @param projectId The ID of the project the configuration group should be added to
     */
    public void addConfigGroup(final String name, int projectId) {
        postRESTBody(TestRailCommand.ADD_CONFIG_GROUP.getCommand(), null,
                new BaseEntity() {
                    @JsonProperty("name")
                    private String nameString = name;
                });
    }

    /**
     * Creates a new configuration group.
     * @param name The name of the configuration
     * @param configGroupId The ID of the configuration group the configuration should be added to
     */
    public void addConfig(final String name, int configGroupId) {
        postRESTBody(TestRailCommand.ADD_CONFIG.getCommand(), Integer.toString(configGroupId),
                new BaseEntity() {
                    @JsonProperty("name")
                    private String nameString = name;
                });
    }

    /**
     * Updates an existing configuration group.
     * @param name The new name of the configuration group
     * @param configGroupId The ID of the configuration group
     */
    public void updateConfigGroup(final String name, int configGroupId) {
        postRESTBody(TestRailCommand.UPDATE_CONFIG_GROUP.getCommand(), Integer.toString(configGroupId),
                new BaseEntity() {
                    @JsonProperty("name")
                    private String nameString = name;
                });
    }

    /**
     * Updates an existing configuration.
     * @param name The new name of the configuration
     * @param configId The ID of the configuration
     */
    public void updateConfig(final String name, int configId) {
        postRESTBody(TestRailCommand.UPDATE_CONFIG.getCommand(), Integer.toString(configId),
                new BaseEntity() {
                    @JsonProperty("name")
                    private String nameString = name;
                });
    }

    /**
     * Updates an existing configuration group.
     * Please note: Deleting a configuration group cannot be undone and also permanently deletes all configurations in this group. It does not, however, affect closed test plans/runs, or active test plans/runs unless they are updated.
     * @param configGroupId The ID of the configuration group
     */
    public void deleteConfigGroup(int configGroupId) {
        postRESTBody(TestRailCommand.DELETE_CONFIG_GROUP.getCommand(), Integer.toString(configGroupId), null);
    }

    /**
     * Deletes an existing configuration.
     * Please note: Deleting a configuration cannot be undone. It does not, however, affect closed test plans/runs, or active test plans/runs unless they are updated.
     * @param configId The ID of the configuration
     */
    public void deleteConfig(int configId) {
        postRESTBody(TestRailCommand.DELETE_CONFIG.getCommand(), Integer.toString(configId), null);
    }

    //API: Milestones-------------------------------------------------

    /**
     * Returns the Milestone object with the given ID
     * @param milestoneId the ID of the Milestone you're interested in
     * @return The Milestone object
     */
    public Milestone getMilestone(int milestoneId) {
        return getEntitySingle(Milestone.class, TestRailCommand.GET_MILESTONE.getCommand(), Integer.toString(milestoneId));
    }

    /**
     * Returns a list of all the Milestones in the given project ID
     * @param projectId the ID of project you want the Milestones from
     * @param isCompleted only accepts ApiFilter.IS_COMPLETED (numerical boolean {0|1})
     * @return the list of all the Milestones in the project
     */
    public List<Milestone> getMilestones(int projectId, ApiFilterValue... isCompleted) {
        return getEntityList(Milestone.class, TestRailCommand.GET_MILESTONES.getCommand(), Integer.toString(projectId) + isCompleted[0].append());
    }

    /**
     * Creates a new milestone.
     * @param milestone The EmptyMilestone object with parameters for creating a new milestone
     * @param projectId The ID of the project the milestone should be added to
     * @return Returns the new milestone that was created
     */
    public Milestone addMilestone(EmptyMilestone milestone, int projectId) {
        return postRESTBodyReturn(TestRailCommand.ADD_MILESTONE.getCommand(), Integer.toString(projectId), milestone, Milestone.class);
    }

    public Milestone updateMilestone(int milestoneId, final boolean isCompleted) {
        return postRESTBodyReturn(TestRailCommand.UPDATE_MILESTONE.getCommand(),
                Integer.toString(milestoneId),
                new BaseEntity() {
                    @JsonProperty("is_completed")
                    private String isCompletedBoolean = isCompleted ? "1":"0";
                },
                Milestone.class);
    }


    //API: Plans------------------------------------------------------

    /**
     * The TestPlan assocated with the indicated id
     * @param planId The id of the TestPlan you're interested in
     * @return The TestPlan entity indicated by the id
     */
    public TestPlan getTestPlan(int planId) {
        return getEntitySingle(TestPlan.class, TestRailCommand.GET_PLAN.getCommand(), Integer.toString(planId));
    }

    /**
     * The List of TestPlan entities the indicated Project contains
     * @param projectId The id of the project you're interested in
     * @return A List of TestPlan entities for the indicated Project
     */
    public List<TestPlan> getTestPlans( int projectId ) {
        return getEntityList(TestPlan.class, TestRailCommand.GET_PLANS.getCommand(), Integer.toString(projectId));
    }

    /**
     * Adds a Test Plan in TestRails
     * @param projectId the ID of the project to add the Test Plan to
     * @param testPlan the skeleton Test Plan object the TestRails Test Plan will be based off of
     * @return the completed Test Plan created in TestRails
     */
    public TestPlan addTestPlan(int projectId, TestPlanCreator testPlan) {
        return postRESTBodyReturn(TestRailCommand.ADD_PLAN.getCommand(), Integer.toString(projectId), testPlan, TestPlan.class);
    }

    /**
     * Adds a Test Plan Entry in TestRails
     * @param planId the ID of the Test Plan to add the Test Plan Entry to
     * @param planEntry the skeleton Plane Entry object the TestRails Plan Entry (Test Run) will be based off of
     * @return the completed Plan Entry created in TestRails
     */
    public PlanEntry addTestPlanEntry(int planId, PlanEntry planEntry) {
        return postRESTBodyReturn(TestRailCommand.ADD_PLAN_ENTRY.getCommand(), Integer.toString(planId), planEntry, PlanEntry.class);
    }


    //API: Priorities-------------------------------------------------


    //API: Projects---------------------------------------------------

    /**
     * Returns the Project, specified by id
     * @param projectId The TestRails Project Id
     * @return The Project, or null if it doesn't exist
     */
    public Project getProject(int projectId) {
        return getEntitySingle(Project.class, TestRailCommand.GET_PROJECT.getCommand(), Integer.toString(projectId));
    }

    /**
     * Looks up the Project, specified by Name
     * @param projectName The Name of the Project (including spaces)
     * @return The Project, or null if it doesn't exist
     */
    public Project getProjectByName(String projectName) {
        for (Project project: this.getProjects()) {
            if (project.getName().equals(projectName)) {
                return project;
            }
        }
        return null;
    }

    /**
     * Returns all Project entities related to this account
     * @return The List of ALL Projects available to this user
     */
    public List<Project> getProjects() {
        return getEntityList(Project.class, TestRailCommand.GET_PROJECTS.getCommand(), "");
    }


    //API: Results----------------------------------------------------

    /**
     * Returns the most recent TestResult object for the given TestInstance
     * @param testInstanceId The TestInstance you're interested in (gathered from the specific TestRun)
     * @return The most recent TestResult for the given TestInstance
     */
    public TestResult getTestResult(int testInstanceId) {
        List<TestResult> results = getTestResults(testInstanceId, 1);
        if (null == results || results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Returns a List of the ALL TestResults associated with the indicated TestInstance, most recent first
     * @param testInstanceId The TestInstance id
     * @return A List of TestResults in descending chronological order (i.e. most recent first)
     */
    public List<TestResult> getTestResults(int testInstanceId) {
        if (null == getTestResults(testInstanceId,1)) {
            return null;
        }
        return getEntityList(TestResult.class, TestRailCommand.GET_RESULTS.getCommand(), String.format("%d", testInstanceId));
    }

    /**
     * Returns a List of the TestResults (up to the 'limit' parameter provided) associated with the indicated TestInstance, most recent first
     * @param testInstanceId The TestInstance id
     * @param limit The upper number of TestResults you want to see for this particular TestInstance
     * @return A List of TestResults in descending chronological order (i.e. most recent first)
     */
    public List<TestResult> getTestResults(int testInstanceId, int limit) {
        List<TestResult> results = getEntityList(TestResult.class, TestRailCommand.GET_RESULTS.getCommand(), String.format("%d&limit=%d", testInstanceId, 1));
        if (null == results) {
            return null;
        }
        return getEntityList(TestResult.class, TestRailCommand.GET_RESULTS.getCommand(), String.format("%d&limit=%d", testInstanceId, limit));
    }

    /**
     * Add a TestResult to a particular TestInstance, given the TestInstance id
     * @param testId The id of the TestInstance to which you would like to add a TestResult entity
     * @param result One or more TestResult entities you wish to add to this TestInstance
     */
    public void addTestResult(int testId, TestResult result) {
        HttpResponse response = postRESTBody(TestRailCommand.ADD_RESULT.getCommand(), Integer.toString(testId), result);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException(String.format("TestResult was not properly added to TestInstance [%d]: %s", testId, response.getStatusLine().getReasonPhrase()));
        }
    }

    /**
     * Add a TestResult to a particular TestInstance, given the TestInstance id
     * @param runId The id of the TestRun to which you would like to add a TestResults entity
     * @param results A TestResults entity (which can include multiple TestResult entities) you wish to add to this TestRun
     */
    public void addTestResults(int runId, TestResults results) {
        HttpResponse response = postRESTBody(TestRailCommand.ADD_RESULTS.getCommand(), Integer.toString(runId), results);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException(String.format("TestResults was not properly added to TestRun [%d]: %s", runId, response.getStatusLine().getReasonPhrase()));
        }
    }


    //API: Result Fields----------------------------------------------


    //API: Runs-------------------------------------------------------

    /**
     * Returns TestRun associated with the specific TestRun ID passed in (assuming you know it)
     * @param testRunId The id of the TestRun requested
     * @return The TestRun active for this TestRun ID
     */
    public TestRun getTestRun(int testRunId) {
        return getEntitySingle(TestRun.class, TestRailCommand.GET_RUN.getCommand(), Integer.toString(testRunId));
    }

    /**
     * Returns all the Active TestRuns associated with the given Project
     * @param projectId The id of the Project
     * @return The List of TestRuns currently active for this Project
     */
    public List<TestRun> getTestRuns(int projectId) {
        return getEntityList(TestRun.class, TestRailCommand.GET_RUNS.getCommand(), Integer.toString(projectId));
    }

    /**
     * Add a TestRun via a slimmed down new TestRunCreator entity to get around non-obvious json serialization problems
     * with the TestRun entity
     * @param projectId the id of the project to bind the test run to
     * @returns The newly created TestRun object
     * @throws IOException
     */
    public TestRun addTestRun(int projectId, TestRunCreator run) {
        TestRun newSkeletonTestRun = postRESTBodyReturn(TestRailCommand.ADD_RUN.getCommand(), Integer.toString(projectId), run, TestRun.class);
        TestRun realNewlyCreatedTestRun = getTestRun(newSkeletonTestRun.getId());
        return realNewlyCreatedTestRun;
    }

    /**
     * Complete a TestRun
     */
    public HttpResponse closeTestRun(TestRun run) {
        HttpResponse response = postRESTBody(TestRailCommand.CLOSE_RUN.getCommand(), Integer.toString(run.getId()), run);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException(String.format("TestRun was not properly closed, TestRunID [%d]: %s", run.getId(), response.getStatusLine().getReasonPhrase()));
        }

        return response;
    }


    //API: Sections---------------------------------------------------

    /**
     * Returns the List of Section entities the indicated Project and TestSuite entities contain
     * @param projectId The id of the Project you're interested in
     * @param suiteId The id of the TestSuite you're interested in
     * @return A List of Section entities for the indicated Project/TestSuite
     */
    public List<Section> getSections( int projectId, int suiteId ) {
        return getEntityList(Section.class, TestRailCommand.GET_SECTIONS.getCommand(), String.format("%d&suite_id=%d", projectId, suiteId));
    }


    //API: Statuses---------------------------------------------------


    //API: Suites-----------------------------------------------------

    /**
     * Returns the TestSuite for the id given
     * @param suiteId The Suite ID (in TestRails, this will be something like 'S7', but just provide the 7)
     * @return A TestSuite
     */
    public TestSuite getTestSuite( int suiteId ) {
        return getEntitySingle(TestSuite.class, TestRailCommand.GET_SUITE.getCommand(), Long.toString(suiteId));
    }

    /**
     * Returns all the TestSuites for the project id given
     * @param projectId The Project ID (in TestRails, this will be something like 'P7', but just provide the 7)
     * @return A List of Suites
     */
    public List<TestSuite> getTestSuites( int projectId ) {
        return getEntityList(TestSuite.class, TestRailCommand.GET_SUITES.getCommand(), Long.toString(projectId));
    }


    //API: Templates--------------------------------------------------


    //API: Tests------------------------------------------------------

    /**
     * Returns all TestInstances associated with the given TestRun
     * @param testRunId The id of the TestRun you're interested in
     * @return The List of TestInstances associated with this TestRun
     */
    public List<TestInstance> getTests(int testRunId) {
        return getEntityList(TestInstance.class, TestRailCommand.GET_TESTS.getCommand(), Integer.toString(testRunId));
    }


    //API: Users------------------------------------------------------

    /**
     * Get a user by id
     */
    public User getUserById(int id) {
        return getEntitySingle(User.class, TestRailCommand.GET_USER_BY_ID.getCommand(), "" + id);
    }

    /**
     * Get a user by email address
     */
    public User getUserByEmail(String email) {
        return getEntitySingle(User.class, TestRailCommand.GET_USER_BY_EMAIL.getCommand(), "&email=" + email);
    }

    /**
     * Get the entire list of users from the API
     */
    public List<User> getUsers() {
        return getEntityList(User.class, TestRailCommand.GET_USERS.getCommand(), "");
    }

    //================================================================
    //END API HELPER METHODS



    /**
     * Builds the proper TestRails request URL based on the type and number of parameters. It tries to be smart about how to add
     * parameters to calls that require 0, 1, or 2 arguments
     * @param apiCall The end-point you wish to request
     * @param urlParams The full parameters of the request you're making (it's up to you to make it correct)
     * @return The URL you've built
     */
    private String buildRequestURL(String apiCall, String urlParams) {
        //Some API calls take 2 parameters, like get_cases/16/1231, so we need to account for both
        String argString = "";
        if (!StringUtils.isEmpty(urlParams)) {
            argString = String.format("/%s", urlParams);
        }

        //Build the complete url
        return String.format(apiEndpoint, apiCall, argString);
    }

    /**
     * Makes the specified call to the API using either 1 or 2 args. These args will be validated and inserted before making the actual GET request
     * @param apiCall The specific call to make to the API (NOT including the URL)
     * @param urlParams The first parameter
     * @return An active, open connection in a post-response state
     */
    private HttpURLConnection getRESTRequest(String apiCall, String urlParams) {
        String completeUrl = buildRequestURL(apiCall, urlParams);

        try {
            //log the complete url
            log.debug("url: {}", completeUrl);

            //Add the application/json header
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Content-Type", "application/json");

            //Log the curl call for easy reproduction
//            log.warn(utils.getCurlCommandStringGet(completeUrl, headers));

            String authentication = HTTPUtils.encodeAuthenticationBase64(username, password);
            return utils.getHTTPRequest(completeUrl, authentication, headers);
        } catch (IOException e) {
            log.error("An IOException was thrown while trying to process a REST Request against URL: {}", completeUrl);
        }

        throw new RuntimeException(String.format( "Connection is null (probably hit timeout), check parameters for [%s]", completeUrl));
    }

    /**
     * Posts the given String to the given TestRails end-point
     * @param apiCall The end-point that expects to receive the entities (e.g. "add_result")
     * @param urlParams The remainder of the URL required for the POST. It is up to you to get this part right
     * @param entity The BaseEntity object to use at the POST body
     * @return The Content of the HTTP Response
     */
    private HttpResponse postRESTBody(String apiCall, String urlParams, BaseEntity entity) {
        HttpClient httpClient = new DefaultHttpClient();
        String completeUrl = buildRequestURL( apiCall, urlParams );

        try {
            HttpPost request = new HttpPost( completeUrl );
            String authentication = HTTPUtils.encodeAuthenticationBase64(username, password);
            request.addHeader("Authorization", "Basic " + authentication);
            request.addHeader("Content-Type", "application/json");

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
            byte[] body = mapper.writeValueAsBytes(entity);
            request.setEntity(new ByteArrayEntity(body));

            HttpResponse response = executeRequestWithRetry(request, 2);
            if (response.getStatusLine().getStatusCode() != 200) {
                Error error = JSONUtils.getMappedJsonObject(Error.class, utils.getContentsFromHttpResponse(response));
                log.error("Response code: {}", response.getStatusLine().getStatusCode());
                log.error("TestRails reported an error message: {}", error.getError());
                request.addHeader("Encoding", "UTF-8");
            }
            return response;
        }
        catch (IOException e) {
            log.error(String.format("An IOException was thrown while trying to process a REST Request against URL: [%s]", completeUrl), e.toString());
            throw new RuntimeException(String.format("Connection is null, check URL: %s", completeUrl));
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }
    
    /**
     * Posts the given String to the given TestRails end-point
     *
     * @param apiCall The end-point that expects to receive the entities (e.g. "add_result")
     * @param urlParams The remainder of the URL required for the POST. It is up to you to get this part right
     * @param entity The BaseEntity object to use at the POST body
     * @param returnEntityType The Class of the return type you wish to receive (helps avoid casting from the calling method)
     * @return The Content of the HTTP Response
     */
    private <T extends BaseEntity> T postRESTBodyReturn(String apiCall, String urlParams, BaseEntity entity, Class<T> returnEntityType) {
        HttpClient httpClient = new DefaultHttpClient();
        String completeUrl = buildRequestURL( apiCall, urlParams );

        try {
            HttpPost request = new HttpPost( completeUrl );
            String authentication = HTTPUtils.encodeAuthenticationBase64(username, password);
            request.addHeader("Authorization", "Basic " + authentication);
            request.addHeader("Content-Type", "application/json");
            request.addHeader("Encoding", "UTF-8");

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
            byte[] body = mapper.writeValueAsBytes(entity);
            request.setEntity(new ByteArrayEntity(body));

            HttpResponse response = executeRequestWithRetry(request, 2);
            if (response.getStatusLine().getStatusCode() != 200) {
                Error error = JSONUtils.getMappedJsonObject(Error.class, utils.getContentsFromHttpResponse(response));
                log.error("Response code: {}", response.getStatusLine().getStatusCode());
                log.error("TestRails reported an error message: {}", error.getError());
            } else if (response.getStatusLine().getStatusCode() == 200) {
            	log.info("Returning a JSON mapped object from calling api intergration point");
            	return JSONUtils.getMappedJsonObject(returnEntityType, utils.getContentsFromHttpResponse(response));
            } else {
            	log.error("Unhandled return code for postRESTBodyReturn");
            }
        }
        catch (IOException e) {
            log.error(String.format("An IOException was thrown while trying to process a REST Request against URL: [%s]", completeUrl), e);
            throw new RuntimeException(String.format("Connection is null, check URL: %s", completeUrl), e);
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
		return null;
    }

    /**
     * Execute POST request with retry
     * @param request
     * @param retries
     * @return
     * @throws IOException
     */
    private HttpResponse executeRequestWithRetry(HttpPost request, int retries) throws IOException {
        boolean connected = false;
        int RETRY_DELAY_MS = 0;
        int retryDelayInMS;

        HttpClient httpClient = new DefaultHttpClient();
        HttpResponse response = null;

        for (int retry = 0; retry < retries && !connected; retry++) {
            if (retry > 0) {
                log.warn("retry " + retry + "/" + retries);
                try {
                    log.debug("Sleeping for retry: " + RETRY_DELAY_MS);
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    // lets ignore this
                }
            }

            // try posting request
            response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == 429) {
                log.warn(" **429 for POST**");
                retryDelayInMS = Integer.parseInt(response.getFirstHeader("Retry-After").getValue()) * 1000; // sec to ms
                RETRY_DELAY_MS = retryDelayInMS;  // set delay and retry
            } else {
                break; // if not 429, break
            }
        }
        return response;
    }
}

