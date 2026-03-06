import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_ROOT_URL,
});

export default api;
