import org.apache.spark.sql.SparkSession


object Main extends App {

    val spark = SparkSession.builder()
        .appName("TP1_DMB")
        .master("local[*]")
        .getOrCreate()

    import spark.implicits._

    // ------- Exercice 1 --------

    // Question 1 : Chargement du jeu de données
    val textFile = spark.read.textFile("agg_match_stats_0_100000.csv")
/*
    // Question 2 : On garde seulement le nom du joueur et son nombre de kills
    val name_kills = textFile.map(line => line.split(","))
        .map(fields => (fields(10), fields(11)))

    // Question 3

    val rdd_nbr_parties = textFile
        .map(line => line.split(","))
        .map(fields => (fields(11), 1))
        .rdd
    val rdd_nbr_kills = textFile
        .map(line => line.split(","))
        .map(fields => (fields(11), fields(10).toFloat))
        .rdd

    // On récupère le nombre de partie pour un joueur
    var nbr_parties = rdd_nbr_parties.reduceByKey(_+_).collect()

    // On récupère le nombre de kills pour un joueur
    var nbr_kills = rdd_nbr_kills.reduceByKey(_+_)

    // On calcule la moyenne de kills pour chaque joueur
    //var moyenne_kills = nbr_kills.map(x => (x._1, x._2 /nbr_parties.filter(y => y._1 == x._1)(0)._2), nbr_parties.filter(y => y._1 == x._1)(0)._2))

    // Question 4 : On garde seulement les joueurs ayant fait le plus de kills
    //var bests_10 = moyenne_kills.sortBy(_._2, false).take(10)

    // Question 5 : On garde seulement les joueurs ayant fait au moins 4 parties
    var many_games = nbr_kills.filter(fields => fields._1 != "") // Question 6 : On enlève les joueurs ayant un nom vide
    .filter(x => nbr_parties.filter(y => y._1 == x._1)(0)._2 > 3)

    // Question 7 : On réaffiche à nouveau les 10 meilleurs joueurs
    var moyenne_kills_many_games = many_games.map(x => (x._1, x._2 /nbr_parties.filter(y => y._1 == x._1)(0)._2, nbr_parties.filter(y => y._1 == x._1)(0)._2))
    var bests_10_many_games = moyenne_kills_many_games.sortBy(_._2, false).take(10).foreach(println)

*/
    // ------- Exercice 2 --------

    // Question 1 : Fonction de score
    val score = textFile.map(line => line.split(",")).rdd
        .map(fields => (fields(11), fields(5).toInt * 50 + fields(9).toInt + fields(10).toInt * 100 + 1000 - fields(14).toInt * 10))

    val bests_10_score = score.sortBy(_._2, false).take(10).foreach(println)

    // Conclusion : le classement est très différent avec ce nouveau calcul de score
    spark.stop()
}
