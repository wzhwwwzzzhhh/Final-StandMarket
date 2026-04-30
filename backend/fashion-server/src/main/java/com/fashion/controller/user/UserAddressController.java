package com.fashion.controller.user;

import com.fashion.entity.AddressBook;
import com.fashion.dto.AddressBookDTO;
import com.fashion.result.Result;
import com.fashion.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 用户端地址管理控制器
 */
@RestController
@RequestMapping("/user/address")
public class UserAddressController {

    @Autowired
    private AddressBookService addressBookService;

    /**
     * 添加地址
     */
    @PostMapping
    public Result<String> add(@RequestBody AddressBookDTO addressBookDTO) {
        try {
            // 转换为AddressBook实体
            AddressBook addressBook = new AddressBook();
            addressBook.setConsignee(addressBookDTO.getConsignee());
            addressBook.setPhone(addressBookDTO.getPhone());
            addressBook.setProvinceCode(addressBookDTO.getProvinceCode());
            addressBook.setProvinceName(addressBookDTO.getProvinceName());
            addressBook.setCityCode(addressBookDTO.getCityCode());
            addressBook.setCityName(addressBookDTO.getCityName());
            addressBook.setDistrictCode(addressBookDTO.getDistrictCode());
            addressBook.setDistrictName(addressBookDTO.getDistrictName());
            addressBook.setDetail(addressBookDTO.getDetail());
            addressBook.setIsDefault(addressBookDTO.getIsDefault());
            
            addressBookService.add(addressBook);
            return Result.success("地址添加成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除地址
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            addressBookService.delete(id);
            return Result.success("地址删除成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新地址
     */
    @PutMapping
    public Result<String> update(@RequestBody AddressBookDTO addressBookDTO) {
        try {
            // 转换为AddressBook实体
            AddressBook addressBook = new AddressBook();
            addressBook.setId(addressBookDTO.getId());
            addressBook.setConsignee(addressBookDTO.getConsignee());
            addressBook.setPhone(addressBookDTO.getPhone());
            addressBook.setProvinceCode(addressBookDTO.getProvinceCode());
            addressBook.setProvinceName(addressBookDTO.getProvinceName());
            addressBook.setCityCode(addressBookDTO.getCityCode());
            addressBook.setCityName(addressBookDTO.getCityName());
            addressBook.setDistrictCode(addressBookDTO.getDistrictCode());
            addressBook.setDistrictName(addressBookDTO.getDistrictName());
            addressBook.setDetail(addressBookDTO.getDetail());
            addressBook.setIsDefault(addressBookDTO.getIsDefault());
            
            addressBookService.update(addressBook);
            return Result.success("地址更新成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询地址
     */
    @GetMapping("/{id}")
    public Result<AddressBook> getById(@PathVariable Long id) {
        AddressBook addressBook = addressBookService.getById(id);
        return Result.success(addressBook);
    }

    /**
     * 查询用户的地址列表
     */
    @GetMapping("/list")
    public Result<List<AddressBook>> list() {
        List<AddressBook> addressBookList = addressBookService.list();
        return Result.success(addressBookList);
    }

    /**
     * 查询用户的默认地址
     */
    @GetMapping("/default")
    public Result<AddressBook> getDefault() {
        AddressBook addressBook = addressBookService.getDefault();
        return Result.success(addressBook);
    }

    /**
     * 设置默认地址
     */
    @PutMapping("/default/{id}")
    public Result<String> setDefault(@PathVariable Long id) {
        try {
            addressBookService.setDefault(id);
            return Result.success("默认地址设置成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}