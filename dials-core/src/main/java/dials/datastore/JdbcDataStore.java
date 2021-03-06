package dials.datastore;

import dials.filter.FeatureFilterDataBean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

public class JdbcDataStore implements DataStore {

    private DataSource dataSource;

    public JdbcDataStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public FeatureFilterDataBean getFiltersForFeature(String featureName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        FeatureFilterDataBean dataBean = new FeatureFilterDataBean();

        String query = "select is_enabled, filter_name, data_key, data_value from dials_feature df join dials_feature_filter dff "
                + "on df.feature_id = dff.feature_id left join dials_feature_filter_static_data dffsd "
                + "on dff.feature_filter_id = dffsd.feature_filter_id where feature_name = ? order by filter_name asc";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(query, featureName);

        for (Map<String, Object> result : results) {
            dataBean.addFilterData((String) result.get("filter_name"), (String) result.get("data_key"), result.get("data_value"));
        }

        return dataBean;
    }

    public boolean doesFeatureExist(String featureName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String query = "select feature_id from dials_feature where feature_name = ?";

        List<Integer> exists = jdbcTemplate.queryForList(query, Integer.class, featureName);

        if (exists == null || exists.isEmpty()) {
            return false;
        }

        return true;
    }

    public boolean isFeatureEnabled(String featureName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String query = "select is_enabled from dials_feature where feature_name = ?";

        List<Boolean> enabled = jdbcTemplate.queryForList(query, Boolean.class, featureName);

        if (enabled == null || enabled.isEmpty() || !enabled.get(0)) {
            return false;
        }

        return true;
    }

    @Override
    public synchronized void registerAttempt(String featureName, boolean executed) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        int executionValue = executed ? 1 : 0;

        int rowCount = jdbcTemplate.update("update dials_feature_execution set attempts = (attempts + 1), "
                + "executions = (executions + ?) where feature_id = "
                + "(select feature_id from dials_feature where feature_name = ?)", executionValue, featureName);

        if (rowCount == 0) {
            jdbcTemplate.update("insert into dials_feature_execution (feature_id, attempts, executions, errors) "
                    + "values ((select feature_id from dials_feature where feature_name = ?), 1, ?, 0)", featureName, executionValue);
        }
    }

    @Override
    public void registerError(String featureName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.update("update dials_feature_execution set errors = (errors + 1) "
                + "where feature_id = (select feature_id from dials_feature where feature_name = ?)", featureName);
    }

    @Override
    public CountTuple getExecutionCountTuple(String featureName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<Map<String, Object>> counts = jdbcTemplate.queryForList("select executions, errors from dials_feature_execution "
                + "where feature_id = (select feature_id from dials_feature where feature_name = ?)", featureName);

        if (counts == null || counts.isEmpty()) {
            return null;
        }

        return new CountTuple((int) counts.get(0).get("executions"), (int) counts.get(0).get("errors"));

    }

    @Override
    public void updateStaticData(String featureName, String dial, String newValue) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.update("update dials_feature_filter_static_data set data_value = ? where data_key = ? and feature_filter_id = (select "
                + "feature_filter_id from dials_feature_filter dff join dials_feature df on dff.feature_id = df.feature_id "
                + "where feature_name = ?)", newValue, dial, featureName);
    }
}
