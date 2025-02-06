import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import scala.io.StdIn.readLine
import org.apache.spark.rdd.RDD

object Main extends App {

 /*   val spark = SparkSession.builder()
        .appName("PROJECT_DMB")
        .master("local[*]")
        .getOrCreate()*/
    val sparkConf = new SparkConf().setAppName("PROJECT_DMB").setMaster("local[*]")
    val sc = new SparkContext(sparkConf)
    //import sc.implicits._

    // ------- Chargement du jeu de données --------

    val textFile = sc.textFile("high_popularity_spotify_data.csv")

    // ------- Question 1 : Quelles sont les 10 musiques les plus populaires ? -------

    println("\n------- Question 1 : Quelles sont les 10 musiques les plus populaires ? -------\n")
    val rdd_music_popularity = textFile.map(line => line.split(","))
                                    .map(fields => (fields(17), fields(7), fields(10).toInt))

    val bests_10_popularity = rdd_music_popularity.distinct().sortBy(_._3, false).take(10)
    println("\n10 best musics : \n")
    bests_10_popularity.foreach(println)

    // --------- Question 2 : Quels sont les genres les plus rapides (tempo) ? ----------

    println("\n------- Question 2 : Quels sont les genres les plus rapides (tempo) ? -------\n")
    val rdd_genre_tempo = textFile.map(line => line.split(","))
                                    .map(fields => (fields(3), fields(1).toFloat))

    val rdd_nbr_music_by_genre = textFile.map(line => line.split(","))
                                    .map(fields => (fields(3), 1))

    val nbr_music_by_genre = rdd_nbr_music_by_genre.reduceByKey(_+_).collect()
    val total_tempo_by_genre = rdd_genre_tempo.reduceByKey(_+_)
    val mean_tempo_by_genre = total_tempo_by_genre.map(p => (p._1, p._2 / nbr_music_by_genre.filter(y => y._1 == p._1)(0)._2))
    val fastest_genre = mean_tempo_by_genre.sortBy(_._2, false).collect()
    println("\nFastest genres : \n")
    fastest_genre.foreach(println)

    // -------- Question 3 : Quelles musiques recommander en fonction des préférences de l'utilisateur ? --------

    println("\n------- Question 3 : Quelles musiques recommander en fonction des préférences de l'utilisateur ? -------\n")
    print("Which tempo do you prefer, fast (0), medium (1) or slow (2) ? : ")
    val tempo = readLine()

    print("Which level of energy do you prefer, very energetic (0), medium (1) or calm (2) ? : ")
    val energy = readLine()

    print("Do you want your song to be acoustic (0), or not (1) ? : ")
    val acousticness = readLine()

    val slow_tempo = 100.0
    val fast_tempo = 150.0

    val low_energy = 0.4
    val high_energy = 0.8

    val acoustic = 0.5

    val rdd_recommandations = textFile.map(line => line.split(","))
                                    .map(fields => (fields(17), fields(7), fields(0).toFloat, fields(1).toFloat, fields(24).toFloat))

    var recommandations : RDD[(String, String, Float, Float, Float)] = rdd_recommandations
    println("\nTempo : "+tempo+"\n")
    tempo match {
        case "0" => {
            recommandations = rdd_recommandations.filter(x => x._4 > fast_tempo).sortBy(_._4,false)
        }
        case "1" => {
            recommandations = rdd_recommandations.filter(x => x._4 > slow_tempo && x._4 < fast_tempo).sortBy(_._4,false)
        }
        case "2" => {
            recommandations = rdd_recommandations.filter(x => x._4 < slow_tempo).sortBy(_._4,true)
        }
        case _ => println("Invalid input")
    }

    val firstFiltered = recommandations.distinct().take(200)
    val rddFiltered = sc.parallelize(firstFiltered)
    energy match {
        case "0" => {
            recommandations = rddFiltered.filter(x => x._3 > high_energy).sortBy(_._3,false)
        }
        case "1" => {
            recommandations = rddFiltered.filter(x => x._3 > low_energy && x._3 < high_energy).sortBy(_._3,false)
        }
        case "2" => {
            recommandations = rddFiltered.filter(x => x._3 < low_energy).sortBy(_._3,true)
        }
        case _ => println("Invalid input")
    }

    val secondFiltered = recommandations.take(100)
    val rddSecondFiltered = sc.parallelize(secondFiltered)
    acousticness match {
        case "0" => {
            recommandations = rddSecondFiltered.filter(x => x._5 > acoustic).sortBy(_._5, false)
        }
        case "1" => {
            recommandations = rddSecondFiltered.filter(x => x._5 < acoustic).sortBy(_._5, true)
        }
        case _ => println("Invalid input")
    }
    val finalRecommandations: Array[(String, String, Float, Float, Float)] = recommandations.take(15)

    println("\nRecommandations : \n")
    finalRecommandations.foreach(println)
}
