import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import scala.Console.println

object app extends App {

  val helper = new HelpfulFunctions()

  val sparkConf = new SparkConf().setAppName("graphXTP").setMaster("local[1]")
  val sc = new SparkContext(sparkConf)

  val DATASET_PATH = "JC-202112-citibike-tripdata.csv"
  /*  Graph creation  */
  var trips = sc.textFile(DATASET_PATH)
  trips = trips.filter(line => !line.contains(",,"))


  /* 1- Création de graphe */

  case class Trip(ride_id : String,rideable_type: String,started_at: Long,ended_at: Long,member_casual: String)
  case class Station(station_id: String, station_name: String, station_latitude: Double, station_longitude: Double)

val start_stations = trips.map(line => line.split(","))
        .map(fields => (fields(4), fields(5), fields(8), fields(9)))
val end_stations = trips.map(line => line.split(","))
        .map(fields => (fields(6), fields(7), fields(10), fields(11)))

val stations = start_stations.union(end_stations).distinct()

  //Convertissez chaque élément de l'RDD trips en objet de classe Trip.

  val stationsRDD: RDD[(VertexId, Station)] = stations.zipWithIndex().map{case (line, index) =>
    (index + 1L, Station(line._2, line._1, line._3.toDouble, line._4.toDouble))
  }e

  val stationMap: Map[String, VertexId] = stationsRDD.map{case (id, station) => (station.station_id, id)}.collect().toMap


val tripsRDD: RDD[Edge[Trip]] = trips.map{line =>
    val row = line split ','
    (Edge(stationMap(row(5)), stationMap(row(7)), Trip(row(0), row(1), helper.timeToLong(row(2)), helper.timeToLong(row(3)), row(12))))
  }

  //Contruisez le graphe à partir de usersRDD et followersRDD

  val graph : Graph[Station, Trip] =
    Graph(stationsRDD, tripsRDD)

  graph.vertices.collect()
  graph.edges.collect()

// 2- Calcul de degré
//  1. subGraph

graph.subgraph(trip => (trip.attr.started_at > helper.timeToLong("2021-12-05 00:00:00") && trip.attr.ended_at < helper.timeToLong("2021-12-25 00:00:00")))

//  2. aggregateMessages : get number of leaving trips from each station name and associate it with the station name

val leavingTrips = graph.aggregateMessages[Int](_.sendToSrc(1), _+_)
val enteringTrips = graph.aggregateMessages[Int](_.sendToDst(1), _+_)

// we map the id of the station with the name
val leavingTripsWithNames = leavingTrips.join(graph.vertices)
  .map { case (vertexId, (count, station)) =>
    (station.station_name, count)
  }
val enteringTripsWithNames = enteringTrips.join(graph.vertices)
  .map { case (vertexId, (count, station)) =>
      (station.station_name, count)
  }

// sort + get top 10

val top10leaving = leavingTripsWithNames.sortBy(_._2, ascending = false).take(10)
val top10entering = enteringTripsWithNames.sortBy(_._2, ascending = false).take(10)

println("Top 10 stations with the most leaving trips")
top10leaving.foreach(println)

println("Top 10 stations with the most entering trips")
top10entering.foreach(println)

// 3- Proximité entre les stations :
//    1. Distance la plus courte

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

/*val distances = JC013distZero.pregel(Double.MaxValue, 2, EdgeDirection.Out)(
    (id, station, newDistance) => {
        //println("station distance = " + station.distance + ", newDistance = " + newDistance)
        if (station.distance > newDistance) {
            station.copy(distance = newDistance) // Mettre à jour si une distance plus courte est trouvée
        } else {
            station
        }
    },
    triplet => {
        val helperDist = helper.getDistKilometers(triplet.srcAttr.station_longitude, triplet.srcAttr.station_latitude, triplet.dstAttr.station_longitude, triplet.dstAttr.station_latitude)
        val dist = triplet.srcAttr.distance + helperDist
        //println("id = " + triplet.srcAttr.station_id + " distance = " + helperDist)
        if(triplet.srcAttr.station_id == "JC013") {
            println("src = " + triplet.srcAttr.station_name + ", dist = " + triplet.srcAttr.distance)
        }
        if (dist < triplet.dstAttr.distance) {
            Iterator((triplet.dstId, dist))
        }
        else {
            Iterator.empty
        }
    },
    (a,b) => math.min(a,b)
)*/


val dist = Pregel(graphWithDist.mapVertices((_, vd) => vd.copy(distance = if (vd.station_id == "JC013") 0.0 else Double.MaxValue)),
        initialMsg=Double.MaxValue, maxIterations=2,
        activeDirection = EdgeDirection.Out) (
        (_:VertexId, vd:StationWithDist, a:Double) => {
            println("actual dist : " + vd.distance + ", new dist : " + a + ", min : " + math.min(a, vd.distance))
            StationWithDist(vd.station_id, vd.station_name, vd.station_latitude, vd.station_longitude, math.min(a, vd.distance))
        },
        (et:EdgeTriplet[StationWithDist, Trip]) => {
            val helperDist = helper.getDistKilometers(et.srcAttr.station_longitude, et.srcAttr.station_latitude, et.dstAttr.station_longitude, et.dstAttr.station_latitude)
            println("helperDist : " + helperDist + " srcAttr : " + et.srcAttr.distance + " dstAttr : " + et.dstAttr.distance)
            if (et.srcAttr.distance + helperDist < et.dstAttr.distance) {
                Iterator((et.dstId, et.srcAttr.distance + helperDist))
            }
            else {
                Iterator.empty
            }
        },
        (a:Double, b:Double) => {
            math.min(a,b)
        })

dist.vertices.foreach(
{ case (id, station) => println(station.station_name + " is at distance " + station.distance + " from JC013")})

  /*  Extraction de sous-graphe

  /*

  Affichez l'entourage des noeuds Lady gaga et Barack Obama avec une solution qui fait appel
  à la fonction \textit{subgraph}.

   */

  val desiredUsers = List("ladygaga", "BarackObama")

  toyGraph.subgraph(edgeTriplet => desiredUsers contains edgeTriplet.srcAttr.name )
    .triplets.foreach(t => println(t.srcAttr.name + " is followed by " + t.dstAttr.name))

  /*  Computation of the vertices' Degrees  */

  /*

  Calculez le degré (Nombre de relations sortantes) de chaque noeuds en faisant appel
  à la méthode \texttt{aggregateMessage}.

   */

  val outDegreeVertexRDD = toyGraph.aggregateMessages[Int](_.sendToSrc(1), _+_)
  outDegreeVertexRDD.collect().foreach(println)

  /* Calcul de la distance du plus long chemin sortant de chaque noeud. */

  /*

  Créez une nouvelle classe \texttt{UserWithDistance} pour inclure la distance aux noeuds du graphe.

  */

  case class UserWithDistance(name: String, nickname: String, distance: Int)

  /*
  * Transformez les VD des noeuds du graphe en type UserWithDistance et initialisez la distance à 0.

  * Définissez la méthode vprog (VertexId, VD, A) => VD. Cette fonction met à jour les données
    VD du noeud ayant un identifiant VD en prenant en compte le message A

  * Définissez la méthode sendMsg EdgeTriplet[VD, ED] => 	Iterator[(VertexId, A)].
    Ayant les données des noeuds obtenues par la méthode vprog VD et des relations ED.
    Cette fonction traite ces données pour créer un message $A$ et l'envoyer ensuite au noeuds destinataires.

  * Définissez la méthode mergeMsg (A, A) => A. Cette méthode est identique à la méthode reduce
    qui traite tous les messages reçus par un noeud et les réduit en une seule valeur.

  *

   */

  val toyGraphWithDistance = Pregel(toyGraph.mapVertices((_, vd) => UserWithDistance(vd.name, vd.nickname, 0)),
        initialMsg=0, maxIterations=2,
        activeDirection = EdgeDirection.Out) (
        (_:VertexId, vd:UserWithDistance, a:Int) => UserWithDistance(vd.name, vd.nickname, math.max(a, vd.distance)),
        (et:EdgeTriplet[UserWithDistance, Int]) => Iterator((et.dstId, et.srcAttr.distance+1)),
        (a:Int, b:Int) => math.max(a,b))
  toyGraphWithDistance.vertices.foreach(user =>println(user))
*/}
