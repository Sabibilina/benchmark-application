import type { RecommendationResponse } from "../types/recommendation";
import { api } from "./client";

export async function fetchDailyMix(): Promise<RecommendationResponse> {
  const response = await api.recommendation.get<RecommendationResponse>("/recommend/daily-mix");
  return response.data;
}

export async function fetchSimilar(songId: string): Promise<RecommendationResponse> {
  const response = await api.recommendation.get<RecommendationResponse>(`/recommend/similar/${songId}`);
  return response.data;
}
