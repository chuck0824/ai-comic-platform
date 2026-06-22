import request from './request'

export const storyboardApi = {
  parseStoryboard: (storyboardId, data) => request.post(`/storyboards/${storyboardId}/parse`, data),
  updateShot: (storyboardId, shotId, data) => request.patch(`/storyboards/${storyboardId}/shots/${shotId}`, data),
  generateImages: (storyboardId, data) => request.post(`/storyboards/${storyboardId}/generate-images`, data),
  generateVideos: (storyboardId, data) => request.post(`/storyboards/${storyboardId}/generate-videos`, data)
}
