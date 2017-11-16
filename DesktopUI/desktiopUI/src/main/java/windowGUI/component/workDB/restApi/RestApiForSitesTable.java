package windowGUI.component.workDB.restApi;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import java.util.ArrayList;

public interface RestApiForSitesTable {
/*
* <Получение>
* запросы с помощью которых, можно получить данные из БД
* */
    @GET("admin/ui/getAllSites")
    Call<ArrayList<PojoSites>> getListAllSites();
/*
* </Получение>
* */

/*
* <Отправка>
* запросы с помощью которых, можно отправить данные в БД
* */
    @POST("admin/ui/addSite")
    Call<ResponseBody> addSite(@Query("SiteName") String siteName ,
                               @Query("SiteURL") String siteUrl,
                               @Query("siteActive") boolean siteActive);// добавляет сайт, URL и активность в БД

    @POST("admin/ui/delSite")
    Call<ResponseBody> delSite(@Query("SiteID") int siteID);// удаляет сайт по ID из БД

    @POST("admin/ui/modifySite")
    Call<ResponseBody> modifySite(@Query("SiteID") int siteID,
                                  @Query("SiteName") String siteName ,
                                  @Query("SiteURL") String siteUrl,
                                  @Query("siteActive") boolean siteActive);// редактирует имя сайта его URL и активность по ID  сайта(сам ID редоктировать нельзя)
/*
* </Отправка>
*/
}