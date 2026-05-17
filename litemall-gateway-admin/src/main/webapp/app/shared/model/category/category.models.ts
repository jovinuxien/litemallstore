export type CategoryData = {
  id: number;
  categoryId: {
    id: number
  },
  name: string;
  desc: string;
  pid: number;
  iconUrl: string;
  picUrl: string;
  level: number;
  sortOrder: number;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
  children?: CategoryData[];
};


interface allCategoryData {
  idCategory: CategoryData[];
}

export interface CategoryCurrentResult {
  currentCategory: CategoryData | null;
  currentSubCategory: CategoryData[];
}
export interface CategoryIndexResult {
  currentCategory: CategoryData | null;
  categoryList: CategoryData[];
  subCategoryList: CategoryData[];
}

export interface CategoryAllResult {
  allList: allCategoryData;
  currentCategory: CategoryData | null;
  categoryList: CategoryData[];
  subCategoryList: CategoryData[];
}

export interface L1CategoryList {
  l1CatList: CategoryData[]
}

export interface IBrand {
  id?: number;
  name?: string;
  desc?: string;
  picUrl?: string;
  floorPrice: Float32Array;
}

export interface IBanner {
  id?: number;
  name?: string;
  link?: string;
  url?: string;
  position?: number;
  content?: string;
  enabled?: true;
  addTime: Date;
  updateTime: Date;
  deleted: false;
}
