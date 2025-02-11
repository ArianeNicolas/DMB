import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import scala.Console.println

object app extends App {

    val helper = new HelpfulFunctions()

    // Création de la session Spark
    val sparkConf = new SparkConf().setAppName("graphXTP").setMaster("local[1]")
    val sc = new SparkContext(sparkConf)

    // --------- 1. Préparation des données --------------

    // Chargement du dataset
    val DATASET_PATH = "JC-202112-citibike-tripdata.csv"

    //  1. Création des classes du graphes
    var trips = sc.textFile(DATASET_PATH)
    trips = trips.filter(line => !line.contains(",,")) // on enlève toutes les lignes où il manque des données

    case class Trip(ride_id : String,rideable_type: String,started_at: Long,ended_at: Long,member_casual: String)
    case class Station(station_id: String, station_name: String, station_latitude: Double, station_longitude: Double)

    //  2. On collecte les informations des stations dans deux rdd différentes
    val start_stations = trips.map(line => line.split(","))
            .map(fields => (fields(4), fields(5), fields(8), fields(9)))
    val end_stations = trips.map(line => line.split(","))
            .map(fields => (fields(6), fields(7), fields(10), fields(11)))

    // Puis on fait un union sur ces listes pour obtenir toutes les stations existantes
    val stations = start_stations.union(end_stations).distinct()

    //  3. On convertit chaque objet Station en Vertex du graphe
    val stationsRDD: RDD[(VertexId, Station)] = stations.zipWithIndex().map{case (line, index) =>
        (index + 1L, Station(line._2, line._1, line._3.toDouble, line._4.toDouble))
    }

    // On crée une map pour obtenir facilement les id des stations pour contruire les edges
    val stationMap: Map[String, VertexId] = stationsRDD.map{case (id, station) => (station.station_id, id)}.collect().toMap

    //  4. On construit les edges à l'aide des objets Trips et des id des Stations
    val tripsRDD: RDD[Edge[Trip]] = trips.map{line =>
        val row = line split ','
        (Edge(stationMap(row(5)), stationMap(row(7)), Trip(row(0), row(1), helper.timeToLong(row(2)), helper.timeToLong(row(3)), row(12))))
    }

    //  5. On construit le graphe à l'iade de nos deux RDD Vertex et Edge
    val graph : Graph[Station, Trip] = Graph(stationsRDD, tripsRDD)

    graph.vertices.collect()
    graph.edges.collect()

    // ----------------- 2. Calcul de degré -----------------

    /*  1. On extrait le sous-graphe des trajets situés entre le 5/12/2021, et le 25/12/2021
        On utilise pour cela la fonction helper.timeToLong()
    */
    graph.subgraph(trip => (trip.attr.started_at > helper.timeToLong("2021-12-05 00:00:00") && trip.attr.ended_at < helper.timeToLong("2021-12-25 00:00:00")))

    /*  2. On calcule le nombre de trajets sortants, et le nombre de trajets entrants pour chaque station
        On utilise aggregateMessages en envoyant respectivement à la source et à la destination un message,
        et en faisant la somme des messages reçus (_+_)
    */
    val leavingTrips = graph.aggregateMessages[Int](_.sendToSrc(1), _+_)
    val enteringTrips = graph.aggregateMessages[Int](_.sendToDst(1), _+_)

    // On associe le nom de la station à son Id pour plus de clarté
    val leavingTripsWithNames = leavingTrips.join(graph.vertices)
    .map { case (vertexId, (count, station)) =>
        (station.station_name, count)
    }
    val enteringTripsWithNames = enteringTrips.join(graph.vertices)
    .map { case (vertexId, (count, station)) =>
        (station.station_name, count)
    }

    //  3. Enfin, on trie par ordre décroissant et on prend les 10 premières stations, et on affiche

    val top10leaving = leavingTripsWithNames.sortBy(_._2, ascending = false).take(10)
    val top10entering = enteringTripsWithNames.sortBy(_._2, ascending = false).take(10)

    println("\nTop 10 stations with the most leaving trips\n")
    top10leaving.foreach(println)

    /* Résultat :
        Top 10 stations with the most leaving trips

        (Grove St PATH,2355)
        (Hoboken Terminal - River St & Hudson Pl,2111)
        (Sip Ave,1670)
        (Hoboken Terminal - Hudson St & Hudson Pl,1640)
        (Newport PATH,1298)
        (Hamilton Park,1290)
        (City Hall - Washington St & 1 St,1242)
        (South Waterfront Walkway - Sinatra Dr & 1 St,1233)
        (Newport Pkwy,1206)
        (Marin Light Rail,1087)
    */

    println("&\nTop 10 stations with the most entering trips\n")
    top10entering.foreach(println)

    /* Résultat :
        Top 10 stations with the most entering trips

        (Grove St PATH,2375)
        (Hoboken Terminal - River St & Hudson Pl,2026)
        (Hoboken Terminal - Hudson St & Hudson Pl,1677)
        (Sip Ave,1518)
        (Hamilton Park,1352)
        (City Hall - Washington St & 1 St,1296)
        (South Waterfront Walkway - Sinatra Dr & 1 St,1296)
        (Newport PATH,1261)
        (Newport Pkwy,1206)
        (Hoboken Ave at Monmouth St,1126)
    */

    // ---------------- 3. Proximité entre les stations : -------------
    /*  1. Distance la plus courte
        Ici on utilise à nouveau la fonction aggregateMessage.
        Cette fois, si aucune des stations du triplet (edge + 2 vertex) n'est JC013, on envoie la distance Double.maxValue (infini)
        Sinon, on envoie la distance entre la station source et JC013, calculée à l'aide de helper.getDistKilometers.
        On fait cela pour chaque triplet du graphe, et on garde à chaque fois la distance la plus courte.
    */

    val distToJC013 = graph.aggregateMessages[Double](
    triplet => {
        if (triplet.srcAttr.station_id == "JC013" && triplet.dstAttr.station_id == "JC013") {
            triplet.sendToDst(Double.MaxValue)
        }
        else if (triplet.srcAttr.station_id == "JC013") {
            triplet.sendToDst(helper.getDistKilometers(triplet.srcAttr.station_longitude, triplet.srcAttr.station_latitude, triplet.dstAttr.station_longitude, triplet.dstAttr.station_latitude))
        }
        else if (triplet.dstAttr.station_id == "JC013") {
            triplet.sendToSrc(helper.getDistKilometers(triplet.srcAttr.station_longitude, triplet.srcAttr.station_latitude, triplet.dstAttr.station_longitude, triplet.dstAttr.station_latitude))
        }
        else {
            triplet.sendToDst(Double.MaxValue)
        }
    },
        (a,b) => math.min(a,b)
    )

    val distToJC013WithNames = distToJC013.join(graph.vertices)
    .map { case (vertexId, (count, station)) =>
        (station.station_name, count)
    }

    // Pour finir, on trie par ordre croissant et on affiche la première station (la plus proche de JC013).
    val shortestDistance = distToJC013WithNames.sortBy(_._2).take(1)
    println("\nClosest station to JC013 :")
    shortestDistance.foreach(println)

    /* Résultat :
        Closest station to JC013 :
        (City Hall,0.3607075419709036)
    */

    // 2. Durée la plus courte
    /*
        On fait la même chose que pour la distance, mais cette fois au lieu d'utiliser helper.getDistKilometers,
        on envoie la durée du trajet, calculée en soustrayant les dates de début et de fin du trajet.
    */
    val timeToJC013 = graph.aggregateMessages[Double](
    triplet => {
        if (triplet.srcAttr.station_id == "JC013" && triplet.dstAttr.station_id == "JC013") {
            triplet.sendToDst(Double.MaxValue)
        }
        else if (triplet.srcAttr.station_id == "JC013") {
            triplet.sendToDst(triplet.attr.ended_at - triplet.attr.started_at)
        }
        else if (triplet.dstAttr.station_id == "JC013") {
            triplet.sendToSrc(triplet.attr.ended_at - triplet.attr.started_at)
        }
        else {
            triplet.sendToDst(Double.MaxValue)
        }
    },
        (a,b) => math.min(a,b)
    )

    val timeToJC013WithNames = timeToJC013.join(graph.vertices)
    .map { case (vertexId, (count, station)) =>
        (station.station_name, count)
    }

    // De la même manière, on trie par ordre croissant et on affiche la première station (la durée la plus courte vers JC013).
    val shortestTime = timeToJC013WithNames.sortBy(_._2).take(1)
    println("\nFastest trip to JC013 :")
    shortestTime.foreach(println)

    /* Résultat :
        Fastest trip to JC013 :
        (City Hall,93000.0)
    */

    // 4- Bonus, plus court chemin
    /*
        Pour ce bonus, l'objectif est de trouver le plus court chemin entre JC013 et toutes les autres stations du graphe,
        parfois en passant par d'autres stations si elles ne sont pas directement reliées.
    */

    // On commence par recréer une classe Station, qui cette fois contient un attribut distance
    case class StationWithDist(station_id: String, station_name: String, station_latitude: Double, station_longitude: Double, distance: Double)

    // On reconstruit un graphe de la même manière que dans la question 1
    val start_stations_with_dist = trips.map(line => line.split(","))
            .map(fields => (fields(4), fields(5), fields(8), fields(9), Double.MaxValue))
    val end_stations_with_dist = trips.map(line => line.split(","))
            .map(fields => (fields(6), fields(7), fields(10), fields(11), Double.MaxValue))

    val stations_with_dist = start_stations_with_dist.union(end_stations_with_dist).distinct()

    val stations_with_distRDD: RDD[(VertexId, StationWithDist)] = stations_with_dist.zipWithIndex().map{case (line, index) =>
        (index + 1L, StationWithDist(line._2, line._1, line._3.toDouble, line._4.toDouble, line._5))
    }

    val graphWithDist : Graph[StationWithDist, Trip] =
        Graph(stations_with_distRDD, tripsRDD)

    // On utilise la fonction Pregel pour calculer le plus court chemin
    val dist = Pregel(graphWithDist.mapVertices((_, vd) => vd.copy(distance = if (vd.station_id == "JC013") 0.0 else Double.MaxValue)), // on commence par initialiser la distance à Double.MaxValue pour toutes les stations sauf JC013
        initialMsg=Double.MaxValue, maxIterations=2,
        activeDirection = EdgeDirection.Out) (
        /* Tout d'abord, on crée une fonction de mise à jour de la distance suivant le message reçu.
            On compare la distance actuelle de la station avec la nouvelle distance reçue, et on garde la plus petite.
            On renvoie une nouvelle Station avec la distance mise à jour.
        */
        (_:VertexId, vd:StationWithDist, a:Double) => {
            StationWithDist(vd.station_id, vd.station_name, vd.station_latitude, vd.station_longitude, math.min(a, vd.distance))
        },
        /*
            Ensuite, on crée une fonction d'envoi de message.
            Si la distance de la source + la distance entre la source et la destination est plus petite que la distance actuelle de la destination,
            on envoie un message avec la nouvelle distance. Sinon, on n'envoie rien.
        */
        (et:EdgeTriplet[StationWithDist, Trip]) => {
            val helperDist = helper.getDistKilometers(et.srcAttr.station_longitude, et.srcAttr.station_latitude, et.dstAttr.station_longitude, et.dstAttr.station_latitude)
            if (et.srcAttr.distance + helperDist < et.dstAttr.distance) {
                Iterator((et.dstId, et.srcAttr.distance + helperDist))
            }
            else {
                Iterator.empty
            }
        },
        /*
            Enfin, on crée une fonction de fusion de messages.
            On garde à chaque fois la distance la plus petite.
        */
        (a:Double, b:Double) => {
            math.min(a,b)
        }
    )

    /* Résultat :
        Nous n'avons pas compris pourquoi mais notre Pregel n'a pas fonctionné, la distance était toujours égale à Double.maxValue...
    */
}
