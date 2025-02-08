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

    println("&\nTop 10 stations with the most entering trips\n")
    top10entering.foreach(println)

    // ---------------- 3. Proximité entre les stations : -------------
    //  1. Distance la plus courte

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

    distToJC013WithNames.sortBy(_._2).take(1).foreach(println)

    //     2. Durée la plus courte

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

    timeToJC013WithNames.sortBy(_._2).take(1).foreach(println)

    // 4- Bonus, plus court chemin

    case class StationWithDist(station_id: String, station_name: String, station_latitude: Double, station_longitude: Double, distance: Double)

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
    //val JC013distZero = graphWithDist.mapVertices((_, vd) => vd.copy(distance = if (vd.station_id == "JC013") 0 else Double.MaxValue))
