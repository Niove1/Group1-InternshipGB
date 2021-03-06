import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by ferney on 12.11.17.
 */
public class DBWrapper {

    final int BUCKET_TYPE_SITES = 1;
    final int BUCKET_TYPE_PAGES = 2;

    final int HOURS_BEFORE_UPDATE = 23;

    private Connection connection = null;

    public DBWrapper() {

        final String PATH_TO_PROPERTIES = "config.properties";
        Properties prop = new Properties();
        InputStream input = null;
        // Load properties from file


        try {
            try {

                input = new FileInputStream(PATH_TO_PROPERTIES);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            try {

                prop.load(input);

            } catch (IOException e) {
                e.printStackTrace();
            }
            connection =
                    DriverManager.getConnection(
                            prop.getProperty("database"),
                            prop.getProperty("dbuser"),
                            prop.getProperty("dbpassword"));

            connection.setAutoCommit(false);
            //connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getBucketlistSql(int bucketType, int size) {
        if (bucketType == BUCKET_TYPE_SITES) {
            return "SELECT \"ID\", \"URL\" FROM sites s " +
                    "WHERE " +
                    "\"InProgress\" = false " +
                    "AND (" +
                    "(SELECT count(1) FROM pages p WHERE p.\"SiteID\" = s.\"ID\") = 0 " +
                    "AND \"LastUpdated\" < NOW() - INTERVAL '" + HOURS_BEFORE_UPDATE + " hours') " +
                    "LIMIT " + size + " FOR UPDATE";
        } else if (bucketType == BUCKET_TYPE_PAGES) {
            return "SELECT pages.\"ID\" as \"ID\", pages.\"URL\" as \"URL\", " +
                    "sites.\"ID\" as \"SiteID\", sites.\"URL\" as \"SiteURL\"" +
                    "FROM pages, sites " +
                    "WHERE " +
                    "sites.\"ID\" = pages.\"SiteID\" " +
                    "AND (pages.\"LastScanDate\" < NOW() - INTERVAL '" + HOURS_BEFORE_UPDATE + " hours' " +
                    "OR pages.\"LastScanDate\" IS NULL) " +
                    "AND pages.\"InProgress\" = false " +
                    "ORDER BY pages.\"LastScanDate\" " +
                    "LIMIT " + size + " FOR UPDATE";
        } else {
            // Unknown bucket type!
            return "";
        }
    }

    private String getBucketlockSql(int bucketType, int size) {
        if (bucketType == BUCKET_TYPE_SITES) {
            return "UPDATE sites SET \"InProgress\" = true WHERE \"ID\" = ?";
        }
        if (bucketType == BUCKET_TYPE_PAGES) {
            return "UPDATE pages SET \"InProgress\" = true WHERE \"ID\" = ?";
        } else {
            // Unknown bucket type!
            return "";
        }
    }

    private Page createNewPage(int bucketType, long pageId, String pageUrl, long siteId, String siteUrl) {
        if (bucketType == BUCKET_TYPE_PAGES) {
            return new Page(pageId, pageUrl);
        } else if (bucketType == BUCKET_TYPE_SITES) {
            return new Page(pageId, pageUrl, siteId, siteUrl);
        } else {
            // unknown bucket type!
            return null;
        }
    }

    public ArrayList<Page> getBucketFromDB(int size, int bucketType) {

        String listSql = getBucketlistSql(bucketType, size);
        String lockSql = getBucketlockSql(bucketType, size);

        LogWrapper.info(listSql);

        Statement listStatement = null;
        PreparedStatement lockStatement = null;

        ResultSet listResultSet = null;

        ArrayList<Page> result = new ArrayList<>();

        try {

            listStatement = connection.createStatement();
            listStatement.executeQuery(listSql);

            listResultSet = listStatement.getResultSet();
            lockStatement = connection.prepareStatement(lockSql);

            while (listResultSet.next()) {
                lockStatement.setInt(1, listResultSet.getInt("ID"));
                lockStatement.execute();
                //result.add(listResultSet.getString("URL"));
                result.add(createNewPage(bucketType,
                        listResultSet.getLong("ID"),
                        listResultSet.getString("URL"),
                        listResultSet.getLong("SiteID"),
                        listResultSet.getString("SiteURL")
                        ));
            }

            connection.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                listResultSet.close();
                listStatement.close();
                lockStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return (result);
    }

    public ArrayList<Page> getSiteBucketFromDB(int size) {
        return getBucketFromDB(size, BUCKET_TYPE_SITES);
    }

    public ArrayList<Page> getPageBucketFromDB(int size) {
        return getBucketFromDB(size, BUCKET_TYPE_PAGES);
    }

    private String getBucketItemUnlockSql(int bucketType) {
        if (bucketType == BUCKET_TYPE_SITES) {
            return "UPDATE sites SET \"InProgress\" = false WHERE \"ID\" = ?";
        } else if (bucketType == BUCKET_TYPE_PAGES) {
            return "UPDATE pages SET \"InProgress\" = false WHERE \"ID\" = ?";
        } else {
            // Unknown bucket type!
            return "";
        }
    }


    public void unlockBucketItem(Page page, int bucketType) {
        PreparedStatement preparedStatement = null;

        try {
            preparedStatement = connection.prepareStatement(getBucketItemUnlockSql(bucketType));
            preparedStatement.setLong(1, page.getPageId());
            preparedStatement.execute();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    public void addSitePage(Page site, String pageUrl) {
        PreparedStatement preparedStatement = null;

        try {
            preparedStatement = connection.prepareStatement("INSERT INTO pages (\"SiteID\", \"URL\", \"FoundDateTime\") " +
                    "VALUES (?, ?, NOW())");
            preparedStatement.setLong(1, site.getPageId());
            preparedStatement.setString(2, pageUrl);

            LogWrapper.info("Running query " + preparedStatement.toString());
            preparedStatement.execute();

        } catch (SQLException e) {
            LogWrapper.info("Query " + preparedStatement.toString() + " failed with exception " + e.getMessage());
            //e.printStackTrace();
        } finally {
            try {
                connection.commit();
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void setSiteScanDate(Page site) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement("UPDATE sites SET \"LastUpdated\" = NOW() WHERE \"ID\" = ?");
            preparedStatement.setLong(1, site.getPageId());
            preparedStatement.execute();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void setPageScanDate(Page page) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement("UPDATE pages SET \"LastScanDate\" = NOW() WHERE \"ID\" = ?");
            preparedStatement.setLong(1, page.getPageId());
            preparedStatement.execute();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    public void unlockSite(Page site) {
        setSiteScanDate(site);
        unlockBucketItem(site, BUCKET_TYPE_SITES);
    }

    public void unlockPage(Page page) {
        unlockBucketItem(page, BUCKET_TYPE_PAGES);
        setPageScanDate(page);
    }

    public ArrayList<Integer> getPersonIDs() {
        ArrayList<Integer> result = new ArrayList<>();
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            statement.execute("SELECT \"ID\" FROM persons");
            resultSet = statement.getResultSet();
            while (resultSet.next()) {
                result.add(resultSet.getInt("ID"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public ArrayList<String> getPersonKeywords(int personId) {
        ArrayList<String> result = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = connection.prepareStatement("SELECT \"Name\" FROM keywords WHERE \"PersonID\" = ?");
            preparedStatement.setInt(1, personId);
            preparedStatement.execute();

            resultSet = preparedStatement.getResultSet();

            while (resultSet.next()) {
                result.add(resultSet.getString("Name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            e.printStackTrace();
        } finally {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public void updatePersonPageRating(int rank, int personId, Page page) {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement =
                    connection.prepareStatement("INSERT INTO personpagerank "
                            + "(\"Rank\", \"PersonID\", \"PageID\", \"RankDate\") "
                            + "VALUES (?, ?, ?, NOW()) "
                            + "ON CONFLICT (\"PersonID\", \"PageID\", \"RankDate\") DO UPDATE SET \"Rank\" = ? "
                            + "WHERE personpagerank.\"PersonID\" = ? "
                            + "AND personpagerank.\"PageID\" = ? "
                            + "AND personpagerank.\"RankDate\" = NOW()");

            preparedStatement.setInt(1, rank);
            preparedStatement.setInt(2, personId);
            preparedStatement.setLong(3, page.getPageId());
            preparedStatement.setInt(4, rank);
            preparedStatement.setInt(5, personId);
            preparedStatement.setLong(6, page.getPageId());

            LogWrapper.info(preparedStatement.toString());

            preparedStatement.execute();

            connection.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    public boolean doesPageExist(String url) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement("SELECT \"ID\" FROM pages WHERE \"URL\" = ?");
            preparedStatement.setString(1, url);
            preparedStatement.execute();

            if (preparedStatement.getResultSet().next()) {
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;

    }

}
