import axios from 'axios';
import * as XLSX from 'xlsx';

// Export the goods catalogue to xlsx / csv / json. Fetches every page from the
// admin list endpoint (authenticated via the global axios interceptor), maps
// rows to a flat export shape, and downloads as a Blob. The flat columns match
// the import template (GoodsImportDialog), so an export round-trips as an
// import file.

export type ExportFormat = 'xlsx' | 'csv' | 'json';

const LIST_URL = '/srv/private/admin/goods/list';
const PAGE_SIZE = 200;
const MAX_ROWS = 10000; // hard cap so a runaway catalogue can't hang the tab

interface ExportRow {
  id?: number;
  goodsSn?: string;
  name?: string;
  categoryId?: number;
  brand?: string;
  picUrl?: string;
  counterPrice?: number;
  retailPrice?: number;
  stock?: number | null;
  brief?: string;
  keywords?: string;
  isOnSale?: boolean;
  salesQuantity?: number;
  addTime?: string;
}

const priceNum = (value: unknown): number => {
  if (value == null) return 0;
  if (typeof value === 'number') return value;
  const amount = (value as { amount?: unknown }).amount;
  return typeof amount === 'number' ? amount : Number(value) || 0;
};

const fetchAllGoods = async (): Promise<ExportRow[]> => {
  const rows: ExportRow[] = [];
  let page = 1;
  for (;;) {
    const response = await axios.get(LIST_URL, { params: { page, limit: PAGE_SIZE, sort: 'add_time', order: 'desc' } });
    const body = response.data;
    if (body?.errno !== 0) {
      throw new Error(body?.errmsg || `list failed (errno ${body?.errno})`);
    }
    const list: Record<string, unknown>[] = body.data?.list ?? [];
    for (const g of list) {
      rows.push({
        id: g.id as number,
        goodsSn: (g.goodsSn as string) ?? '',
        name: (g.name as string) ?? '',
        categoryId: (g.categoryId as number) ?? 0,
        brand: (g.brand as string) ?? '',
        picUrl: (g.picUrl as string) ?? '',
        counterPrice: priceNum(g.counterPrice),
        retailPrice: priceNum(g.retailPrice),
        stock: (g.stock as number) ?? null,
        brief: (g.brief as string) ?? '',
        keywords: (g.keywords as string) ?? '',
        isOnSale: (g.isOnSale as boolean) ?? (g.status === 'ONSALE' ? true : undefined),
        salesQuantity: (g.salesQuantity as number) ?? 0,
        addTime: (g.addTime as string) ?? '',
      });
    }
    const pages: number = body.data?.pages ?? 0;
    if (list.length < PAGE_SIZE || (pages > 0 && page >= pages) || rows.length >= MAX_ROWS) {
      return rows.slice(0, MAX_ROWS);
    }
    page += 1;
  }
};

const download = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

export const exportGoods = async (format: ExportFormat): Promise<void> => {
  const rows = await fetchAllGoods();
  const stamp = new Date().toISOString().slice(0, 10);
  if (format === 'json') {
    download(new Blob([JSON.stringify(rows, null, 2)], { type: 'application/json' }), `goods-${stamp}.json`);
    return;
  }
  const sheet = XLSX.utils.json_to_sheet(rows);
  if (format === 'csv') {
    download(new Blob([XLSX.utils.sheet_to_csv(sheet)], { type: 'text/csv;charset=utf-8' }), `goods-${stamp}.csv`);
    return;
  }
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'goods');
  const buffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' }) as ArrayBuffer;
  download(new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), `goods-${stamp}.xlsx`);
};
