package xyz.koleno.sunwidget.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import xyz.koleno.sunwidget.api.json.ApiResponse

interface DataService {

    @GET("json?formatted=0")
    fun getTimes(@Query("lat") latitude: Double, @Query("lng") longitude: Double): Call<ApiResponse>

}