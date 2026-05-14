export interface RecommendationItem {
  songId: string;
  rank: number;
  reason: string;
}

export interface RecommendationResponse {
  type: string;
  recommendations: RecommendationItem[];
}
