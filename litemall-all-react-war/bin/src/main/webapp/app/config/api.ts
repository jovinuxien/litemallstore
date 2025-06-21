export const BASE_URL_CONTEXT = 'http://localhost:9000/wx';
export const ADMIN_URL_CONTEXT = 'http://localhost:9000/admin';

/* export async function makeApiRequest<T>(endpoint: string, params: Record<string, string | number> = {}): Promise<T> {
  const queryString = new URLSearchParams(params as Record<string, string>).toString();
  const url = `${BASE_URL_CONTEXT}/${endpoint}${queryString ? `?${queryString}` : ''}`;

  try {
    const response = await baseAxios.get<T>(url);
    if (response.data !== 0) {
      throw new Error('API request failed');
    }
    return response.data;
  } catch (error) {
    if (error instanceof Error) {
      throw new Error(error.message);
    }
    throw new Error('Unknown error occurred');
  }
} */
