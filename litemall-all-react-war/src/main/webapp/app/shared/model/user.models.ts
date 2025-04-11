import { Gender } from './enumerations/gender.model';

export interface IUserModel {
  id: number;
  username: string;
  email?: string;
  mobile?: string;
  avatarUrl?: string;
  city?: string;
  language?: string;
  gender: Gender;
}

export interface IUserInfo {
  nickname?: string;
  avatarUrl?: string;
  country?: string;
  province?: string;
  city?: string;
  language?: string;
  gender?: number;
  mobile?: number;
}

export interface IAuhtInfo {
  userInfo?: IUserInfo;
  token?: string;
}

export interface UserProfile {}

export interface IAdminInfo {
  nickname?: string;
  avatar: string;
}
