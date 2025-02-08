import org.apache.spark.sql.SparkSession


object Main extends App {

    // Création de la session Spark
    val spark = SparkSession.builder()
        .appName("TP1_DMB")
        .master("local[*]")
        .getOrCreate()

    import spark.implicits._

    // ------- Exercice 1 : Les meilleurs joueurs --------

    // Question 1 : Chargement du jeu de données

    val textFile = spark.read.textFile("agg_match_stats_0_100000.csv")

    // Question 2 : On garde seulement le nom du joueur et son nombre de kills

    val name_kills = textFile.map(line => line.split(","))
        .map(fields => (fields(10), fields(11)))

    // Question 3 : Moyennes des éliminations pour un joueur

    /* Création de deux rdd,
       un pour compter le nombre de parties pour un joueur,
       et un pour compter son nombre de kills total.
    */
    val rdd_nbr_parties = textFile
        .map(line => line.split(","))
        .map(fields => (fields(11), 1))
        .rdd
    val rdd_nbr_kills = textFile
        .map(line => line.split(","))
        .map(fields => (fields(11), fields(10).toFloat))
        .rdd

    /* On récupère le nombre de parties pour un joueur
        à l'aide de la fonction reduceByKey :
        comme on avait mis un 1 pour chaque ligne dans la deuxième colonne,
        reduceByKey nous rend la somme de tous ces 1,
        donc le nombre total de parties.
    */
    var nbr_parties = rdd_nbr_parties.reduceByKey(_+_).collect()

    /* On récupère le nombre de kills pour un joueur
        Même chose que juste au dessus, sauf que cette fois au lieu des 1
        on a le nombre de kills de la partie,
        la somme donne donc e nombre total de kills du joueur
    */
    var nbr_kills = rdd_nbr_kills.reduceByKey(_+_)

    /* On calcule la moyenne de kills pour chaque joueur,
        en divisant le nombre de kills par le nombre de parties
    */
    var moyenne_kills = nbr_kills.map(x => (x._1, x._2 /nbr_parties.filter(y => y._1 == x._1)(0)._2))

    // Question 4 : On garde seulement les 10 joueurs ayant fait le plus de kills
    var bests_10 = moyenne_kills.sortBy(_._2, false).take(10)

    // Question 5 : On garde seulement les joueurs ayant fait au moins 4 parties
    var many_games = nbr_kills.filter(fields => fields._1 != "") // Question 6 : On enlève les joueurs ayant un nom vide
                .filter(x => nbr_parties.filter(y => y._1 == x._1)(0)._2 > 3)

    // Question 7 : On réaffiche à nouveau les 10 meilleurs joueurs
    var moyenne_kills_many_games = many_games.map(x => (x._1, x._2 /nbr_parties.filter(y => y._1 == x._1)(0)._2, nbr_parties.filter(y => y._1 == x._1)(0)._2))
    var best_10_many_games = moyenne_kills_many_games.sortBy(_._2, false).take(10)
    println("\n10 best players with more than 4 parties: \n")
    best_10_many_games.foreach({ case (p, k, g) => {
        println(p+" : "+k+" kills, in "+g+" parties")
    }})

    /* Résultat :

    10 best players with more than 4 parties:

    LawngD-a-w-n-g : 2.2 kills, in 5 parties
    siliymaui125 : 2.0 kills, in 4 parties
    Dcc-ccD : 1.75 kills, in 4 parties
    dman4771 : 1.75 kills, in 4 parties
    NerdyMoJo : 1.5 kills, in 4 parties
    Roobydooble : 1.0 kills, in 4 parties
    PapaNuntis : 1.0 kills, in 4 parties
    JustTuatuatua : 0.75 kills, in 4 parties
    ChanronG : 0.5 kills, in 4 parties
    China_huangyong : 0.5 kills, in 4 parties

    Conclusion :

        Tout d'abord on observe que pour des joueurs ayant joué plus de 4 parties, le nombre de kills est très faible (1 ou 2 seulement)
        Cela nous donne un indice sur le fait que tuer le maximum de personnes n'est peut-être pas l'objectif principal.
        En comparant avec nos camarades, nous avons vu que les 10 meilleurs joueurs n'étaient
        pas les mêmes si on prend en compte le nombre d'éliminations, ou si on regarde le classement final.
        Cela signifie qu'éliminer un maximum de personne n'est pas forcément l'objectif attendu
    */
    // ------- Exercice 2 --------

    // Question 1 : Fonction de score

    // On commence par mapper le nom de chaque joueur avec son score lors de la partie
    val scores = textFile.map(line => line.split(",")).rdd
        .map(fields => (fields(11), fields(5).toInt * 50 + fields(9).toInt + fields(10).toInt * 100 + 1000 - fields(14).toInt * 10))

    /* Comme précédemment, on ne conserve que les joueur ayant effectué plus de 4 parties.
        Pour cela on utilise la liste déjà créée auparavent many_games,
        et on ne conserve que les joueurs qui ont leur nom dans cette liste.
    */
    val scores_filtered = scores.join(many_games).map { case (p, (s, k)) => (p, s) }

    /* De même, on fait de nouveau la moyenne des différents socres obtenus
        en faisant la somme des scores pour un joueur,
        et en divisant par le nombre de parties effectuées
    */
    val sum_scores = scores_filtered.reduceByKey(_+_)
    val mean_scores = sum_scores.map(x => (x._1, x._2 /nbr_parties.filter(y => y._1 == x._1)(0)._2))

    // Enfin, on garde les 10 meilleurs et on affiche
    val best_10_score = mean_scores.sortBy(_._2, false).take(10)
    println("\n10 best players with score function : \n")
    best_10_score.foreach({ case (p, s) => {
        println(p+" has a score of "+s)
    }})

    /* Résultat :

    10 best players with score function :

    Dcc-ccD has a score of 1302
    LawngD-a-w-n-g has a score of 1220
    dman4771 has a score of 1205
    siliymaui125 has a score of 1172
    JustTuatuatua has a score of 1163
    ChanronG has a score of 1117
    NerdyMoJo has a score of 1093
    PapaNuntis has a score of 1077
    TemcoEwok has a score of 960
    Informaldrip has a score of 938

    Conclusion :

    On retrouve finalement les mêmes éléments dans le classement avec la fonction de score que dans celui du nombre de kills,
    la conclusion est donc que ce sont sont bien ceux qui ont tué le plus d'adversaires qui sont les mieux placés,
    mais que le nombre de kills reste très faible.

    Pour gagner, il faut donc chercher à rester en vie le plus longtemps,
    tuer des adversaires peut rapporter des points mais n'est pas forcément nécessaire.
    */
    spark.stop()
}
