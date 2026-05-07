export interface Leg {
  voyageNumber: string
  loadLocationUnlocode: string
  unloadLocationUnlocode: string
  loadTime: string
  unloadTime: string
}

export interface Itinerary {
  legs: Leg[]
}

export interface RoutingRequest {
  originUnlocode: string
  destinationUnlocode: string
  arrivalDeadline: string | null
}
