import axios from 'axios';
import { BASE_URL_CONTEXT } from './api';

export const baseAxios = axios.create({
  baseURL: BASE_URL_CONTEXT,
  timeout: 5000,
});

export const authAxios = axios.create({
  //baseURL: process.env., // Use environment variables for flexibility
  baseURL: BASE_URL_CONTEXT,
  timeout: 5000,
});

//Request interceptor to add token to requests
authAxios.interceptors.request.use(
  config => {
    if (!config.headers['X-Litemall-Token']) {
      config.headers['X-Litemall-Token'] = `${localStorage.getItem('token') || ''}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle errors and show messages
authAxios.interceptors.response.use(
  response => {
    const res = response.data;
    if (res.errno === 0) {
      return res.data;
      // Logout and redirect to login page
      // Example:
      // window.location.href = '/login';
    } else {
      // Show error message
      console.error(res.errmsg);
      // Handle specific error codes here
      return Promise.reject(res.errmsg);
    }
  },
  error => {
    console.log('err' + error);
    return Promise.reject(error);
  }
);
